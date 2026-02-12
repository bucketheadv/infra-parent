package io.infra.structure.logging

/**
 * PolyLog 注解，用于配置日志流的前缀
 * 可以用于类或方法上
 *
 * @author sven
 * @property value 日志前缀，用于标识日志流
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PolyLog(
    val value: String = ""
)
