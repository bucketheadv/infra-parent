package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleLogHelper
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 单次触发在 JobThread 队列中的票据，供同步 /run 等待执行结果。 */
internal class TriggerTicket(
    val context: JobExecutionContext
) {
    private val future = CompletableFuture<JobExecutionResult>()
    /** 以下状态仅由 [ExecutorJobThread] 的 ticketStateLock 访问。 */
    private var cancelled = false
    private var started = false

    /** 标记取消；返回值表示 Handler 尚未开始，可直接跳过业务调用。 */
    fun cancel(): Boolean {
        cancelled = true
        return !started
    }

    /** 在进入 Handler 前原子确认没有更早的取消请求。 */
    fun startIfNotCancelled(): Boolean {
        if (cancelled) return false
        started = true
        return true
    }

    fun complete(result: JobExecutionResult) {
        future.complete(result)
    }

    fun await(): JobExecutionResult = try {
        future.get()
    } catch (exception: Exception) {
        JobExecutionResult.failure(exception.cause?.message ?: exception.message ?: "等待执行结果失败")
    }
}

/**
 * 对齐 xxl-job `JobThread`：每个 jobId 一条执行线程 + 触发队列。
 * 阻塞策略在入队前判定；COVER_EARLY 会 stop 本线程并由管理器重建。
 *
 * 串行时在 [ticket.complete] 前先 [onExecutionFinished] 回写终态，
 * 避免调度侧 finishLog 落后于下一票 markStarted 而出现多条 RUNNING。
 */
internal class ExecutorJobThread(
    private val jobId: Long,
    private val handlerRegistry: HandlerRegistry,
    private val onExecutionStarted: (JobExecutionContext) -> Unit,
    private val onExecutionFinished: (JobExecutionContext, JobExecutionResult, Long) -> Unit,
    private val onExit: (ExecutorJobThread) -> Unit
) : Thread("infra-schedule-job-$jobId") {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val queue = LinkedBlockingQueue<TriggerTicket>(20_000)
    private val toStop = AtomicBoolean(false)
    /**
     * 串行化队列出队、运行标记和按日志 ID 取消。
     *
     * 取消方要么在此锁内从队列移除票据，要么看到已标记的当前票据并中断；两者之间不存在
     * “已出队但尚未可取消”的窗口。
     */
    private val ticketStateLock = Any()
    @Volatile private var runningHandler = false
    @Volatile private var currentLogId: Long? = null
    /** 已从队列取出、但可能尚未进入 Handler 的当前票据。 */
    private var currentTicket: TriggerTicket? = null

    fun isRunningOrHasQueue(): Boolean = synchronized(ticketStateLock) {
        runningHandler || queue.isNotEmpty()
    }

    fun offer(ticket: TriggerTicket): Boolean = synchronized(ticketStateLock) {
        !toStop.get() && queue.offer(ticket)
    }

    /** 停止线程：清空队列并中断当前 handler。 */
    /** 请求中断并等待线程退出；未确认退出时禁止新线程启动。 */
    fun stopForCover(reason: String, waitMillis: Long): Boolean {
        toStop.set(true)
        val cancelled = JobExecutionResult.cancelled(reason)
        val drained = synchronized(ticketStateLock) {
            // 当前票据可能已经出队、但尚未通过 startIfNotCancelled 进入 Handler。
            // 覆盖请求必须在同一状态锁内标记它，防止旧票据在 drainQueue 之后仍启动业务代码。
            currentTicket?.cancel()
            buildList {
                while (true) add(queue.poll() ?: break)
            }
        }
        completeCancelledTickets(drained, cancelled)
        interrupt()
        return try {
            join(waitMillis.coerceAtLeast(1))
            !isAlive
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /**
     * 按 logId 取消单次触发：
     * - 在队列中则只移除该票并 complete，**不** drain 整队（避免空窗导致乱序/丢序）；
     * - 若正在执行则中断当前 handler，队列其余项保持原顺序继续。
     */
    fun cancel(logId: Long): Boolean {
        val cancellation = synchronized(ticketStateLock) {
            var removed: TriggerTicket? = null
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val ticket = iterator.next()
                if (ticket.context.logId == logId) {
                    iterator.remove()
                    ticket.cancel()
                    removed = ticket
                    break
                }
            }
            val running = removed == null && currentLogId == logId && currentTicket != null
            if (running) currentTicket?.cancel()
            if (running) interrupt()
            removed to running
        }
        val queued = cancellation.first
        if (queued != null) {
            val result = JobExecutionResult.cancelled("任务已被终止")
            onExecutionFinished(queued.context, result, 0L)
            queued.complete(result)
            return true
        }
        return cancellation.second
    }

    fun isRunning(logId: Long): Boolean = synchronized(ticketStateLock) {
        (currentLogId == logId && runningHandler) || queue.any { it.context.logId == logId }
    }

    override fun run() {
        try {
            while (!toStop.get()) {
                val ticket = try {
                    // 在短超时内持有状态锁，保证 poll 与 RUNNING 标记对 cancel 原子可见。
                    // 空闲线程最多每 100ms 醒一次，不会造成繁忙轮询。
                    synchronized(ticketStateLock) {
                        if (toStop.get()) null else queue.poll(100, TimeUnit.MILLISECONDS)?.also {
                            runningHandler = true
                            currentLogId = it.context.logId
                            currentTicket = it
                        }
                    }
                } catch (_: InterruptedException) {
                    if (toStop.get()) break
                    continue
                } ?: continue
                val startedAt = System.currentTimeMillis()
                try {
                    val result = if (synchronized(ticketStateLock) { ticket.startIfNotCancelled() }) {
                        executeBound(ticket.context)
                    } else {
                        JobExecutionResult.cancelled("任务已被终止")
                    }
                    val durationMs = System.currentTimeMillis() - startedAt
                    // 先落终态再 complete，保证下一票 markStarted 时上一条已非 RUNNING。
                    onExecutionFinished(ticket.context, result, durationMs)
                    ticket.complete(result)
                } catch (_: InterruptedException) {
                    val cancelled = JobExecutionResult.cancelled("任务已被终止")
                    onExecutionFinished(ticket.context, cancelled, System.currentTimeMillis() - startedAt)
                    ticket.complete(cancelled)
                    if (toStop.get()) break
                } catch (exception: Throwable) {
                    val message = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                    val failed = JobExecutionResult.failure(message)
                    onExecutionFinished(ticket.context, failed, System.currentTimeMillis() - startedAt)
                    ticket.complete(failed)
                    // AssertionError 等业务 Error 也必须收口当前票据，避免 /run 永久等待。
                    // VM 已无法可靠继续运行时仍交由运行时终止进程，不能掩盖致命故障。
                    if (exception is VirtualMachineError || exception is ThreadDeath || exception is LinkageError) {
                        throw exception
                    }
                } finally {
                    synchronized(ticketStateLock) {
                        runningHandler = false
                        currentLogId = null
                        currentTicket = null
                    }
                    interrupted()
                }
            }
        } finally {
            drainQueue(JobExecutionResult.cancelled("任务线程已退出"))
            onExit(this)
            logger.debug("JobThread 退出: jobId={}", jobId)
        }
    }

    private fun executeBound(context: JobExecutionContext): JobExecutionResult {
        onExecutionStarted(context)
        ScheduleLogHelper.bind(context)
        return try {
            handlerRegistry.execute(context)
        } finally {
            ScheduleLogHelper.flush()
            ScheduleLogHelper.unbind()
        }
    }

    private fun drainQueue(result: JobExecutionResult) {
        val drained = synchronized(ticketStateLock) {
            buildList {
                while (true) add(queue.poll() ?: break)
            }
        }
        completeCancelledTickets(drained, result)
    }

    /** 回写被停止策略撤销的排队票据，保证调度中心日志都能进入终态。 */
    private fun completeCancelledTickets(drained: List<TriggerTicket>, result: JobExecutionResult) {
        drained.forEach { ticket ->
            // 被 COVER_EARLY 或线程退出清空的票据也必须回写终态，避免 Admin 日志永久停留在 QUEUED。
            onExecutionFinished(ticket.context, result, 0L)
            ticket.complete(result)
        }
    }
}
