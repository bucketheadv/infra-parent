package io.infra.structure.trace.admin.model

/**
 * 服务间调用关系，用于拓扑图展示。
 *
 * @author sven
 */
data class TopologyLink(
    /** 调用方服务名 */
    val source: String,
    /** 被调方服务名 */
    val target: String,
    /** 调用总次数 */
    val callCount: Int,
    /** 异常调用次数 */
    val errorCount: Int,
    /** 平均耗时（毫秒） */
    val avgDurationMillis: Double
)
