package io.infra.structure.trace.report

import io.infra.structure.trace.logging.LogEntry

/**
 * 链路日志上报器，把服务进程内采集到的日志发送到追踪后台。
 *
 * 与 [TraceReporter] 一样属于可降级的遥测侧写路径，失败不应影响业务。
 *
 * @author sven
 */
interface LogReporter {

    /**
     * 上报一批日志（同一 traceId 下的多条）。
     *
     * @param logs 待上报的日志条目
     */
    fun reportLogs(logs: List<LogEntry>)
}
