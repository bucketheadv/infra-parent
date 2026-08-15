# infra-trace 分布式日志追踪

基于 MDC（Mapped Diagnostic Context）的轻量级分布式链路追踪模块。其他项目引入本模块后，所有 HTTP 入站请求自动获得 `traceId`（全链路一致）与 `spanId`（每次调用新建），日志与出站调用自动携带链路标识，便于跨服务排查问题。

## 特性

- **入站自动追踪**：Servlet 应用中自动注册过滤器，每个 HTTP 请求生成/透传 traceId 与 spanId，无需业务代码介入。
- **链路语义完整**：`traceId` 整条链路保持一致；每次入站请求新建 `spanId`，上游 spanId 记为 `parentSpanId`，可在日志中还原调用树。
- **出站多客户端传播**：开箱即用的 Ktor / RestTemplate / WebClient / OkHttp 传播组件。
- **异步透传**：提供 `TaskDecorator`，异步线程内日志归属同一条链路。
- **span 上报**：可配置把每次入站请求的 span 异步上报给追踪后台（`infra-trace-admin`），默认关闭，不影响存量调用。
- **可配置**：header 名称、MDC key、开关等均通过 `infra.trace.*` 配置项控制。

## 工作原理

```text
服务A(spanId=a1, parent=上游) ── HTTP(traceId, spanId=a1) ──► 服务B(spanId=b1, parent=a1)
        │                                                          │
        └── 日志: traceId=xxx spanId=a1 parentSpanId=p0            └── 日志: traceId=xxx spanId=b1 parentSpanId=a1
```

| 概念 | MDC key | 生成规则 | 是否跨服务透传 |
|---|---|---|---|
| traceId | `traceId` | 复用请求头，缺失则生成 32 位 hex | 是 |
| spanId | `spanId` | 每次入站请求新建 16 位 hex | 是 |
| parentSpanId | `parentSpanId` | 取入站请求携带的 spanId | 否（仅本服务记录） |

- 入站过滤器：复用或生成 `traceId` → 新建 `spanId` → 写入 MDC → 响应头回写 → 请求结束后清理 MDC（防线程串扰）。
- 出站传播组件：读取当前 MDC 中的 `traceId`/`spanId`，写入出站请求头，下游作为父链路继续接力。

## 快速接入

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.infra.structure</groupId>
    <artifactId>infra-trace</artifactId>
    <version>0.9.6-RELEASE</version>
</dependency>
```

### 2. 引用即生效

MVC 应用引入后无需额外配置，入站请求自动带链路标识。日志模式中加入 MDC 字段即可输出：

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %X{traceId} %X{spanId} - %msg%n</pattern>
```

> 若同时引入 `infra-logging`，其默认日志模式已包含 `%X{traceId}` 与 `%X{spanId}`。

## 出站传播接入

各传播组件以 Bean 形式自动注册（`@ConditionalOnMissingBean`），按需注入后挂到自己的客户端上：

```kotlin
// Ktor（推荐，仓库标准 HTTP 客户端）
val client = HttpClient(CIO) { install(TraceKtorPlugin) }

// RestTemplate
restTemplate.interceptors.add(restTemplateTraceInterceptor)

// WebClient
val webClient = WebClient.builder().filter(webClientTraceFilter).build()

// OkHttp
val client = OkHttpClient.Builder().addInterceptor(okHttpTraceInterceptor).build()
```

异步线程池默认由模块注册的 `TraceTaskDecorator` Bean 透传 MDC；若使用自定义线程池，可注入该 Bean 配置：

```kotlin
@Bean
fun executor(decorator: TaskDecorator): Executor {
    return ThreadPoolTaskExecutor().apply { taskDecorator = decorator }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `infra.trace.enabled` | `true` | 总开关 |
| `infra.trace.header-name` | `X-Request-Id` | traceId 请求头名称 |
| `infra.trace.span-header-name` | `X-Span-Id` | spanId 请求头名称 |
| `infra.trace.mdc-key` | `traceId` | traceId 的 MDC key |
| `infra.trace.span-mdc-key` | `spanId` | spanId 的 MDC key |
| `infra.trace.generate-if-absent` | `true` | 入站未携带 traceId 时是否自动生成 |
| `infra.trace.include-response-header` | `true` | 是否在响应头回写 traceId/spanId |
| `infra.trace.report.enabled` | `false` | 是否开启 span 上报 |
| `infra.trace.report.url` | 空 | 追踪后台采集地址，如 `http://127.0.0.1:18090/api/trace/spans` |
| `infra.trace.report.service-name` | 空 | 上报的服务名，为空时回退 `spring.application.name` |
| `infra.trace.report.timeout-millis` | `3000` | 上报超时（毫秒） |
| `infra.trace.report.capture-request-body` | `false` | 是否采集入参（请求体）一并上报 |
| `infra.trace.report.capture-response-body` | `false` | 是否采集返回值（响应体）一并上报 |
| `infra.trace.report.max-body-length` | `2000` | 入参/返回值采集最大长度（字符），超出截断 |

## span 上报

开启 `infra.trace.report.enabled=true` 并配置 `infra.trace.report.url` 后，每次入站请求结束会自动把本服务这段 span（traceId/spanId/parentSpanId/服务名/路径/耗时/是否成功）异步上报给追踪后台。上报失败仅记录 debug 日志，不影响业务请求。

```yaml
infra:
  trace:
    report:
      enabled: true
      url: http://127.0.0.1:18090/api/trace/spans
      capture-request-body: true
      capture-response-body: true
      max-body-length: 2000
```

- **入参与返回值**：开启 `capture-request-body` / `capture-response-body` 后，过滤器通过缓存包装器采集请求体与响应体（按 `max-body-length` 截断），便于后台查看入参和返回值。注意只有业务真正读取了请求体（如 `@RequestBody`）才会采集到内容；请求被路由层直接拒绝（如 405）时请求体可能为空。
- **异常原因与堆栈**：异常优先取过滤器捕获的向上抛出的异常；若异常由全局异常处理器处理（不会传播到过滤器），请在处理器中调用 `TraceContext.recordError(throwable)` 记录，后台即可展示异常原因与堆栈：

```kotlin
@RestControllerAdvice
class DemoExceptionHandler {
    @ExceptionHandler(Throwable::class)
    fun handle(exception: Throwable): ResponseEntity<*> {
        TraceContext.recordError(exception)   // 让过滤器随 span 上报异常原因与堆栈
        // ...
    }
}
```

配套的 `infra-trace-admin` 提供采集、查询接口与观测台页面（链路列表 + 瀑布图 + span 详情，支持按时间过滤、查看异常原因/堆栈/入参返回值），`infra-trace-service-a` / `infra-trace-service-b` 是演示跨服务链路透传与上报的示例微服务。

## 本地验证

```bash
# 编译
mvn -pl infra-trace -am compile

# 接口验证（假设服务端口 8080）
curl -i http://localhost:8080/your-api
# 期望响应头中包含: X-Request-Id: xxx  X-Span-Id: xxx

# 跨服务链路验证：调用方把响应头中的 X-Request-Id 透传后，两侧日志 traceId 应一致
```
