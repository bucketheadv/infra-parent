package io.infra.structure.trace.admin.model

/**
 * 单条链路的摘要信息，用于列表页展示。
 *
 * @author sven
 */
data class TraceSummary(
    /** 链路唯一标识（32 位 hex） */
    val traceId: String,
    /** 链路中最早 span 的开始时间（Unix 毫秒） */
    val startTimeMillis: Long,
    /** 链路总耗时（最早开始到最晚结束，毫秒） */
    val durationMillis: Long,
    /** 该链路包含的 span 总数 */
    val spanCount: Int,
    /** 涉及的服务名列表（去重） */
    val serviceNames: List<String>,
    /** 链路是否全部成功（所有 span 均无异常） */
    val success: Boolean,
    /** 该 trace 中出现的用户标识（去重） */
    val uids: List<String> = emptyList()
)
