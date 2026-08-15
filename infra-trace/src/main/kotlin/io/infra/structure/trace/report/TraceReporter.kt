package io.infra.structure.trace.report

/**
 * span 上报器，负责把 [TraceSpan] 发送到追踪后台。
 *
 * 上报属于可降级的遥测行为：实现必须自行吞掉底层异常，不得影响业务请求。
 *
 * @author sven
 */
interface TraceReporter {

    fun report(span: TraceSpan)
}
