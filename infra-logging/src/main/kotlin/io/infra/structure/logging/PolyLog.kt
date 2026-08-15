package io.infra.structure.logging

/**
 * PolyLog 注解，用于配置日志流的前缀与传播行为。
 *
 * 可以用于类或方法上。带该注解的类或方法中调用 `LogContext.instance().log()` 收集的日志，
 * 会按传播规则归入同一条日志流输出。
 *
 * @author sven
 * @property value 日志前缀，用于标识日志流
 * @property propagation 日志流传播策略，类似事务传播：
 *   - [PolyLogPropagation.REQUIRED]：外层已有 LogContext 时复用同一日志流（默认）
 *   - [PolyLogPropagation.REQUIRES_NEW]：无论外层是否已有 LogContext，都新开一条独立日志流，
 *     方法结束后立即 flush，随后恢复外层日志流
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PolyLog(
    val value: String = "",
    val propagation: PolyLogPropagation = PolyLogPropagation.REQUIRED
)

/**
 * PolyLog 日志流的传播策略。
 */
enum class PolyLogPropagation {
    /** 外层已有 LogContext 时复用同一日志流（默认，等同事务的 REQUIRED）。 */
    REQUIRED,

    /** 新开一条独立日志流，结束时立即 flush 并恢复外层日志流（等同事务的 REQUIRES_NEW）。 */
    REQUIRES_NEW
}
