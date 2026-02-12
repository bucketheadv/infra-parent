package io.infra.structure.logging.aspect

import io.infra.structure.logging.LogContext
import io.infra.structure.logging.LogLevel
import io.infra.structure.logging.PolyLog
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * PolyLog 注解的 AOP 切面
 * 自动管理 LogContext 的生命周期：
 * - 方法执行前：初始化 LogContext 并设置前缀
 * - 方法执行后：自动 flush 并清理 LogContext
 * - 异常时：也会自动 flush 并清理 LogContext
 *
 * @author sven
 */
@Aspect
@Component
@Order(1)
class PolyLogAspect {

    @Around("@annotation(io.infra.structure.logging.PolyLog) || @within(io.infra.structure.logging.PolyLog)")
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        // 获取注解（优先使用方法上的，如果没有则使用类上的）
        val annotation = getAnnotation(joinPoint)
        
        if (annotation == null || annotation.value.isEmpty()) {
            // 如果没有注解或前缀为空，直接执行方法
            return joinPoint.proceed()
        }

        // 增加嵌套深度
        val depth = LogContext.enter()
        val isOuterMethod = depth == 1
        
        // 如果是外层方法，清除可能存在的旧 context
        if (isOuterMethod) {
            LogContext.clear()
        }
        
        // 获取或创建 context
        val context = LogContext.get()
        // 保存旧的前缀（用于嵌套调用时恢复）
        val oldPrefix = if (isOuterMethod) "" else context.getCurrentPrefix()
        
        // 设置新前缀（嵌套调用时也设置，这样内层的前缀会拼接到日志中）
        context.setPrefix(annotation.value, isNested = !isOuterMethod)
        
        try {
            // 执行方法
            val result = joinPoint.proceed()
            
            // 方法执行成功后，只有最外层方法才 flush 日志
            if (isOuterMethod && !context.isEmpty()) {
                context.flush()
            }
            
            return result
        } catch (e: Throwable) {
            // 发生异常时，只有最外层方法才 flush 日志（使用 ERROR 级别）
            if (isOuterMethod && !context.isEmpty()) {
                context.flush(LogLevel.ERROR)
            }
            throw e
        } finally {
            // 内层方法结束时，恢复外层的前缀
            if (!isOuterMethod && oldPrefix.isNotEmpty()) {
                context.setPrefix(oldPrefix, isNested = true)
            }
            
            // 减少嵌套深度
            val newDepth = LogContext.exit()
            // 只有最外层方法结束时才清理 ThreadLocal
            if (newDepth == 0) {
                LogContext.clear()
            }
        }
    }

    /**
     * 获取 @PolyLog 注解（优先使用方法上的，如果没有则使用类上的）
     */
    private fun getAnnotation(joinPoint: ProceedingJoinPoint): PolyLog? {
        val signature = joinPoint.signature as? MethodSignature ?: return null
        
        // 先查找方法上的注解
        val method = signature.method
        val methodAnnotation = method.getAnnotation(PolyLog::class.java)
        if (methodAnnotation != null && methodAnnotation.value.isNotEmpty()) {
            return methodAnnotation
        }
        
        // 如果方法上没有，查找类上的注解
        val targetClass = joinPoint.target.javaClass
        val classAnnotation = targetClass.getAnnotation(PolyLog::class.java)
        if (classAnnotation != null && classAnnotation.value.isNotEmpty()) {
            return classAnnotation
        }
        
        return null
    }
}
