package io.infra.structure.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * LogContext 类，用于在有 @PolyLog 注解的类或方法中收集和输出日志
 * 
 * 使用 ThreadLocal 存储当前线程的日志上下文，包括前缀和日志列表
 * log() 方法会收集日志，当调用 flush() 时，会将前缀和所有日志拼接成一个字符串输出
 *
 * @author sven
 */
class LogContext private constructor() {
    
    private val logs = mutableListOf<String>()
    private var prefix: String = ""
    private var currentPrefix: String = "" // 当前生效的前缀（用于嵌套调用）
    private var logLevel: LogLevel = LogLevel.INFO
    
    companion object {
        private val contextHolder = ThreadLocal<LogContext>()
        private val depthHolder = ThreadLocal<Int>() // 嵌套深度计数器
        private val logger: Logger = LoggerFactory.getLogger(LogContext::class.java)
        
        /**
         * 获取当前线程的 LogContext，如果不存在则创建
         * 会自动检测当前调用栈中的 @PolyLog 注解来设置前缀
         */
        fun get(): LogContext {
            var context = contextHolder.get()
            if (context == null) {
                context = LogContext()
                // 自动检测前缀
                context.detectPrefix()
                contextHolder.set(context)
            }
            return context
        }
        
        /**
         * 增加嵌套深度（进入带注解的方法时调用）
         */
        fun enter(): Int {
            val depth = depthHolder.get() ?: 0
            depthHolder.set(depth + 1)
            return depth + 1
        }
        
        /**
         * 减少嵌套深度（退出带注解的方法时调用）
         * @return 返回减少后的深度，如果为0表示是最外层方法
         */
        fun exit(): Int {
            val depth = depthHolder.get() ?: 0
            val newDepth = (depth - 1).coerceAtLeast(0)
            depthHolder.set(newDepth)
            return newDepth
        }
        
        /**
         * 清除当前线程的 LogContext
         */
        fun clear() {
            contextHolder.remove()
        }
        
        /**
         * 获取或创建 LogContext，并在方法结束时自动 flush
         * 这是一个便捷方法，可以在方法开始时调用，方法结束时自动 flush
         */
        fun withContext(block: (LogContext) -> Unit) {
            val context = get()
            try {
                block(context)
            } finally {
                if (!context.isEmpty()) {
                    context.flush()
                }
                clear()
            }
        }
        
        /**
         * 记录 INFO 级别日志
         */
        fun info(message: String) {
            get().log(message, LogLevel.INFO)
        }
        
        /**
         * 记录 INFO 级别日志（带格式化参数）
         */
        fun info(format: String, vararg args: Any?) {
            get().log(format, *args, level = LogLevel.INFO)
        }
    }
    
    /**
     * 设置前缀（实例方法）
     * @param prefix 新的前缀
     * @param isNested 是否是嵌套调用（内层方法）
     */
    fun setPrefix(prefix: String, isNested: Boolean = false) {
        if (isNested) {
            // 嵌套调用时，只更新当前前缀，不覆盖外层前缀
            this.currentPrefix = prefix
        } else {
            // 最外层方法，同时设置外层前缀和当前前缀
            this.prefix = prefix
            this.currentPrefix = prefix
        }
    }
    
    /**
     * 获取当前前缀
     */
    fun getCurrentPrefix(): String {
        return currentPrefix
    }
    
    /**
     * 收集一条日志（不立即输出，等待 flush 时统一输出）
     * @param message 日志消息
     * @param level 日志级别，默认为 INFO
     */
    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        // 如果当前前缀不为空，且与外层前缀不同，则在消息前加上当前前缀
        val logMessage = if (currentPrefix.isNotEmpty() && currentPrefix != prefix) {
            "[$currentPrefix] $message"
        } else {
            message
        }
        logs.add(logMessage)
        // 使用最高级别的日志级别（ERROR > WARN > INFO > DEBUG > TRACE）
        if (level.ordinal > logLevel.ordinal) {
            logLevel = level
        }
    }
    
    /**
     * 收集一条日志（带格式化参数，不立即输出，等待 flush 时统一输出）
     * @param format 格式化字符串
     * @param args 格式化参数
     * @param level 日志级别，默认为 INFO
     */
    fun log(format: String, vararg args: Any?, level: LogLevel = LogLevel.INFO) {
        log(String.format(format, *args), level)
    }
    
    /**
     * 输出所有收集的日志（拼接前缀和所有日志）
     * @param level 日志级别，如果指定则使用指定级别，否则使用收集日志时的最高级别
     */
    fun flush(level: LogLevel? = null) {
        // 如果没有日志，直接返回
        if (logs.isEmpty()) {
            return
        }
        
        val fullMessage = buildFullMessage()
        val outputLevel = level ?: logLevel
        
        // 输出日志
        when (outputLevel) {
            LogLevel.TRACE -> logger.trace(fullMessage)
            LogLevel.DEBUG -> logger.debug(fullMessage)
            LogLevel.INFO -> logger.info(fullMessage)
            LogLevel.WARN -> logger.warn(fullMessage)
            LogLevel.ERROR -> logger.error(fullMessage)
        }
        
        // 输出后清空日志列表和级别，但保留前缀
        logs.clear()
        logLevel = LogLevel.INFO
    }
    
    /**
     * 构建完整的日志消息（前缀 + 所有日志）
     */
    private fun buildFullMessage(): String {
        val sb = StringBuilder()
        
        // 如果最外层有前缀，在最外层加上前缀
        if (prefix.isNotEmpty()) {
            sb.append("[$prefix] ")
        }
        
        if (logs.size == 1) {
            sb.append(logs[0])
        } else {
            sb.append(logs.joinToString(" | "))
        }
        
        return sb.toString()
    }
    
    /**
     * 检测当前调用栈中的 @PolyLog 注解来设置前缀
     * 优先查找方法上的注解，如果找不到则查找类上的注解
     */
    private fun detectPrefix() {
        val stackTrace = Thread.currentThread().stackTrace
        
        // 跳过 LogContext 自身的方法，从第3个元素开始查找（跳过 getStackTrace、detectPrefix、get）
        for (i in 3 until stackTrace.size) {
            val element = stackTrace[i]
            val className = element.className
            val methodName = element.methodName
            
            // 跳过 LogContext、反射相关的方法和 lambda 表达式
            if (className.contains("LogContext") || 
                className.contains("java.lang.reflect") ||
                className.contains("sun.reflect") ||
                className.contains("kotlin.jvm.internal") ||
                className.contains("\$")) {
                continue
            }
            
            try {
                val clazz = Class.forName(className)
                
                // 先查找方法上的注解
                val methods = clazz.declaredMethods.filter { it.name == methodName }
                for (method in methods) {
                    val methodAnnotation = method.getAnnotation(PolyLog::class.java)
                    if (methodAnnotation != null && methodAnnotation.value.isNotEmpty()) {
                        prefix = methodAnnotation.value
                        return
                    }
                }
                
                // 如果方法上没有，查找类上的注解
                val classAnnotation = clazz.getAnnotation(PolyLog::class.java)
                if (classAnnotation != null && classAnnotation.value.isNotEmpty()) {
                    prefix = classAnnotation.value
                    return
                }
            } catch (_: Exception) {
                // 忽略无法加载的类（如接口、抽象类等）
                continue
            }
        }
    }
    
    /**
     * 检查是否有日志
     */
    fun isEmpty(): Boolean = logs.isEmpty()
}

/**
 * 日志级别枚举
 */
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
