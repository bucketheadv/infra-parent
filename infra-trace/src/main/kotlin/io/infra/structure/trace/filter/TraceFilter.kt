package io.infra.structure.trace.filter

import io.infra.structure.trace.TraceContext
import io.infra.structure.trace.logging.MemoryLogAppender
import io.infra.structure.trace.properties.TraceProperties
import io.infra.structure.trace.report.LogReporter
import io.infra.structure.trace.report.TraceReporter
import io.infra.structure.trace.report.TraceSpan
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.StandardCharsets
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 入站 HTTP 请求的 traceId/spanId 过滤器。
 *
 * 优先复用请求头中携带的 traceId（跨服务链路透传），缺失时按配置自动生成；
 * 每次入站请求都会新建一个 spanId，入站携带的 spanId 记录为 parentSpanId。
 * 随后写入 MDC 供本服务日志与下游调用使用，并在响应头回写。请求结束后清理 MDC，
 * 避免线程复用导致链路上下文串扰。
 *
 * 当配置了 [TraceReporter]（`infra.trace.report.*`）时，会在请求结束后把本服务这段
 * span 异步上报给追踪后台，供聚合展示调用链。开启 `capture-request-body` /
 * `capture-response-body` 后，会通过缓存包装器采集入参与返回值（超限截断）；异常
 * 优先取 [TraceContext.getError]（供全局异常处理器调用），其次捕获过滤链向上抛出的
 * 异常，一并上报异常原因与堆栈。
 *
 * @author sven
 */
class TraceFilter(
    private val properties: TraceProperties,
    private val reporter: TraceReporter? = null,
    private val serviceName: String? = null,
    private val logReporter: LogReporter? = null
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(TraceFilter::class.java)

    /** 调用栈采样线程池：在请求处理中途采集请求线程栈，从而捕获到业务方法调用 */
    private val callStackSampler: java.util.concurrent.ScheduledExecutorService? =
        if (properties.report.captureCallStack) {
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "trace-callstack-sampler").apply { isDaemon = true }
            }
        } else {
            null
        }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTimeMillis = System.currentTimeMillis()
        val captureBody = properties.report.enabled &&
            (properties.report.captureRequestBody || properties.report.captureResponseBody || properties.report.captureRequestHeaders)
        // 1. 采集入参/返回值时，用缓存包装器包一层；未开启时保持原样避免额外开销
        val requestWrapper = if (captureBody && properties.report.captureRequestBody) {
            ContentCachingRequestWrapper(request, properties.report.maxBodyLength)
        } else {
            null
        }
        val responseWrapper = if (captureBody && properties.report.captureResponseBody) {
            ContentCachingResponseWrapper(response)
        } else {
            null
        }
        val effectiveRequest = requestWrapper ?: request
        val effectiveResponse = responseWrapper ?: response

        // 2. 从请求头提取调用方透传的 traceId；缺失时按配置自动生成
        val headerName = properties.headerName
        val incomingTraceId = effectiveRequest.getHeader(headerName)?.takeIf { it.isNotBlank() }
        val traceId = incomingTraceId ?: if (properties.generateIfAbsent) generateTraceId() else null

        var spanId: String? = null
        var parentSpanId: String? = null
        if (traceId != null) {
            // 3. 每次入站请求新建一个 spanId，调用方携带的 spanId 作为 parentSpanId
            spanId = generateSpanId()
            val incomingParentSpanId = effectiveRequest.getHeader(properties.spanHeaderName)?.takeIf { it.isNotBlank() }

            // 4. 写入 MDC，供本服务日志与出站调用读取
            TraceContext.setTraceId(traceId)
            TraceContext.setSpanId(spanId)
            if (incomingParentSpanId != null) {
                TraceContext.setParentSpanId(incomingParentSpanId)
                parentSpanId = incomingParentSpanId
            }
            // 5. 响应头回写，便于调用方拿到本次链路的 traceId/spanId
            if (properties.includeResponseHeader) {
                effectiveResponse.setHeader(headerName, traceId)
                effectiveResponse.setHeader(properties.spanHeaderName, spanId)
            }
        }
        var propagated: Throwable? = null
        // 6.1 开启调用栈采集时，延迟采样请求线程栈，捕获请求处理途中的业务方法调用
        val sampledCallStack = AtomicReference<Array<StackTraceElement>?>()
        val sampleFuture = if (properties.report.captureCallStack) {
            val requestThread = Thread.currentThread()
            callStackSampler?.schedule(
                { sampledCallStack.set(requestThread.stackTrace) },
                properties.report.callStackSampleDelayMillis,
                TimeUnit.MILLISECONDS
            )
        } else {
            null
        }
        try {
            // 6. 继续过滤链；异步/错误重分发不再重复生成，保证链路上下文一致
            filterChain.doFilter(effectiveRequest, effectiveResponse)
        } catch (throwable: Throwable) {
            propagated = throwable
            throw throwable
        } finally {
            val status = effectiveResponse.status
            // 7. 配置上报时，把本服务这段 span 异步上报给追踪后台（须在清理 MDC 之前读取异常上下文）
            if (traceId != null && spanId != null) {
                sampleFuture?.let { future ->
                    try {
                        // 等待采样完成（限定超时，保证诊断功能不阻塞业务太久）
                        future.get(properties.report.callStackSampleDelayMillis + 200, TimeUnit.MILLISECONDS)
                    } catch (ignored: Exception) {
                        // 采样超时/中断不影响 span 上报
                    }
                }
                reportSpan(
                    traceId, spanId, parentSpanId, effectiveRequest, status,
                    startTimeMillis, propagated, requestWrapper, responseWrapper,
                    callStack = formatCallStack(sampledCallStack.get())
                )
                // 7.1 上报本服务进程内该 traceId 采集到的日志（取出后清空，避免重复上报）
                reportLogs(traceId)
            }
            // 8. 请求结束必须清理 MDC，避免线程池复用导致上下文串扰
            TraceContext.clear()
            // 9. 缓存包装器必须在过滤链完成后把内容回写，否则响应体为空
            if (responseWrapper != null) {
                responseWrapper.copyBodyToResponse()
            }
        }
    }

    override fun shouldNotFilterAsyncDispatch(): Boolean = true

    override fun shouldNotFilterErrorDispatch(): Boolean = true

    /**
     * 组装并上报本次请求的 span；上报为可降级的遥测行为，失败不影响业务。
     */
    private fun reportSpan(
        traceId: String,
        spanId: String,
        parentSpanId: String?,
        request: HttpServletRequest,
        responseStatus: Int,
        startTimeMillis: Long,
        propagated: Throwable?,
        requestWrapper: ContentCachingRequestWrapper?,
        responseWrapper: ContentCachingResponseWrapper?,
        callStack: String? = null
    ) {
        val effectiveServiceName = serviceName?.takeIf { it.isNotBlank() } ?: properties.report.serviceName
        if (reporter == null || !properties.report.enabled ||
            properties.report.url.isBlank() || effectiveServiceName.isBlank()
        ) {
            return
        }
        // 从请求头或请求参数中提取 uid
        val uid = extractUid(request)
        // 采集请求头（在 filterChain 之前，避免被 wrapper 修改）
        val requestHeaders = if (properties.report.captureRequestHeaders) captureHeaders(request) else null
        // 异常优先取全局异常处理器记录的，其次取过滤链向上抛出的
        val error = TraceContext.getError() ?: propagated
        val span = TraceSpan(
            traceId = traceId,
            spanId = spanId,
            parentSpanId = parentSpanId,
            serviceName = effectiveServiceName,
            operation = request.requestURI,
            httpMethod = request.method,
            startTimeMillis = startTimeMillis,
            durationMillis = System.currentTimeMillis() - startTimeMillis,
            success = error == null && responseStatus < 400,
            errorType = error?.javaClass?.name,
            errorMessage = error?.message,
            errorStackTrace = error?.stackTraceToString(),
            requestBody = requestWrapper?.contentAsByteArray?.takeIf { it.isNotEmpty() }
                ?.toString(StandardCharsets.UTF_8)?.take(properties.report.maxBodyLength),
            responseBody = responseWrapper?.contentAsByteArray?.takeIf { it.isNotEmpty() }
                ?.toString(StandardCharsets.UTF_8)?.take(properties.report.maxBodyLength),
            requestHeaders = requestHeaders,
            callStack = callStack,
            uid = uid
        )
        try {
            reporter.report(span)
        } catch (exception: Exception) {
            logger.debug("上报 span 失败，traceId={}", span.traceId, exception)
        }
    }

    /**
     * 把本服务进程内某 traceId 采集到的日志批量上报给追踪后台。
     *
     * 日志由 [MemoryLogAppender] 在本进程按 traceId 缓存；请求结束取出后清空，
     * 保证同一 span 的日志只在本次请求上报一次。上报为可降级行为，失败不影响业务。
     */
    private fun reportLogs(traceId: String) {
        val reporter = logReporter ?: return
        if (!properties.report.enabled || properties.report.logsUrl.isBlank()) return
        val logs = MemoryLogAppender.drainByTraceId(traceId)
        if (logs.isEmpty()) return
        try {
            reporter.reportLogs(logs)
        } catch (exception: Exception) {
            logger.debug("上报日志失败，traceId={}", traceId, exception)
        }
    }

    /**
     * 把调用栈帧格式化为逐行方法名（类名.方法名）。
     *
     * 参照 SkyWalking，只保留业务相关的调用：业务包内的帧（业务 API 入口及其内部方法）与
     * I/O 调用帧（HTTP/MySQL/Redis/MQ 等，配置项 `call-stack-io-prefixes`），
     * 其余框架帧一律丢弃。顺序从"最深层在前"反转为从业务方法开始；连续的 I/O 帧仅保留
     * 靠近业务的一端，避免库内部栈帧刷屏。
     */
    private fun formatCallStack(frames: Array<StackTraceElement>?): String? {
        if (frames == null || frames.isEmpty()) return null
        val businessPrefix = properties.report.callStackIncludePrefix
        val ioPrefixes = properties.report.callStackIoPrefixes
        val filtered = frames.filter { frame ->
            val className = frame.className
            ioPrefixes.any { className.startsWith(it) } ||
                (businessPrefix.isNotBlank() && className.startsWith(businessPrefix))
        }
        if (filtered.isEmpty()) return null
        val ordered = filtered.asReversed()
        val result = mutableListOf<StackTraceElement>()
        for (frame in ordered) {
            val className = frame.className
            val isIo = ioPrefixes.any { className.startsWith(it) }
            val lastIsIo = result.isNotEmpty() &&
                ioPrefixes.any { result.last().className.startsWith(it) }
            if (isIo && lastIsIo) continue
            result.add(frame)
        }
        return result.take(properties.report.callStackMaxDepth)
            .joinToString("\n") { "${it.className}.${it.methodName}" }
            .takeIf { it.isNotBlank() }
    }

    /** traceId 使用 32 位 hex（UUID 去横线），全链路保持一致 */
    private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "")

    /** spanId 使用 16 位 hex（8 字节随机数），每次入站请求唯一 */
    private fun generateSpanId(): String {
        val bytes = ByteArray(8)
        ThreadLocalRandom.current().nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    /** 采集请求头中的关键字段，格式为 "Key: Value" 每行一条 */
    private fun captureHeaders(request: HttpServletRequest): String? {
        val headers = mutableListOf<String>()
        val iterator = request.headerNames
        while (iterator.hasMoreElements()) {
            val name = iterator.nextElement()
            val value = request.getHeader(name)
            if (!value.isNullOrBlank()) {
                headers.add("$name: $value")
            }
        }
        return headers.joinToString("\n").takeIf { it.isNotEmpty() }
            ?.take(properties.report.maxBodyLength)
    }

    /**
     * 从请求头或请求参数中提取 uid。
     *
     * 优先级：X-User-Id header > userId/user_id/uid 参数
     */
    private fun extractUid(request: HttpServletRequest): String? {
        val headerCandidates = listOf("X-User-Id", "X-Uid", "X-UserId")
        for (h in headerCandidates) {
            val v = request.getHeader(h)
            if (!v.isNullOrBlank()) return v
        }
        val paramCandidates = listOf("userId", "user_id", "uid")
        for (p in paramCandidates) {
            val v = request.getParameter(p)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }
}
