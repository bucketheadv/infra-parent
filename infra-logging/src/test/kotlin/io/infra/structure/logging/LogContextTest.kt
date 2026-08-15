package io.infra.structure.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * LogContext 单元测试：覆盖获取/复用、日志收集、flush 输出与清理。
 */
class LogContextTest {

    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(LogContext::class.java) as Logger

    @BeforeEach
    fun setUp() {
        appender.start()
        logger.addAppender(appender)
        LogContext.clear()
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        LogContext.clear()
    }

    @Test
    fun `instance 在同线程内复用同一个上下文`() {
        assertThat(LogContext.instance()).isSameAs(LogContext.instance())
    }

    @Test
    fun `log 收集日志并在 flush 时统一输出`() {
        val context = LogContext.instance()
        context.setPrefix("order")
        context.log("第一条")
        context.log("第二条")
        assertThat(context.isEmpty()).isFalse()

        context.flush()

        assertThat(appender.list).hasSize(1)
        assertThat(appender.list[0].formattedMessage).matches(
            "\\[order] \\(LogContextTest\\.kt:\\d+\\) 第一条 \\| \\(LogContextTest\\.kt:\\d+\\) 第二条"
        )
        assertThat(context.isEmpty()).isTrue()
    }

    @Test
    fun `log 输出的消息包含实际调用文件与行号`() {
        val context = LogContext.instance()
        context.setPrefix("order")
        context.log("业务日志")
        context.flush()

        assertThat(appender.list).hasSize(1)
        assertThat(appender.list[0].formattedMessage).matches("\\[order\\] \\(LogContextTest\\.kt:\\d+\\) 业务日志")
    }

    @Test
    fun `嵌套前缀会拼接到消息中`() {
        val context = LogContext.instance()
        context.setPrefix("outer")
        context.log("外层日志")
        context.setPrefix("inner", isNested = true)
        context.log("内层日志")
        context.setPrefix("outer", isNested = true)
        context.log("恢复外层日志")

        context.flush()

        assertThat(appender.list).hasSize(1)
        assertThat(appender.list[0].formattedMessage).matches(
            "\\[outer\\] \\(LogContextTest\\.kt:\\d+\\) 外层日志 \\| " +
                "\\[inner\\] \\(LogContextTest\\.kt:\\d+\\) 内层日志 \\| " +
                "\\(LogContextTest\\.kt:\\d+\\) 恢复外层日志"
        )
    }

    @Test
    fun `flush 后日志清空但保留前缀`() {
        val context = LogContext.instance()
        context.setPrefix("order")
        context.log("日志一")
        context.flush()
        context.log("日志二")
        context.flush()

        assertThat(appender.list).hasSize(2)
        assertThat(appender.list[0].formattedMessage).matches("\\[order\\] \\(LogContextTest\\.kt:\\d+\\) 日志一")
        assertThat(appender.list[1].formattedMessage).matches("\\[order\\] \\(LogContextTest\\.kt:\\d+\\) 日志二")
    }

    @Test
    fun `没有日志时 flush 不输出`() {
        LogContext.instance().flush()
        assertThat(appender.list).isEmpty()
    }

    @Test
    fun `isActive 反映当前线程上下文存在性`() {
        assertThat(LogContext.isActive()).isFalse()
        LogContext.instance()
        assertThat(LogContext.isActive()).isTrue()
    }

    @Test
    fun `suspend 挂起当前上下文并重置深度 resume 完整恢复`() {
        val outer = LogContext.instance()
        outer.setPrefix("outer")
        LogContext.enter()

        val suspended = LogContext.suspend()
        assertThat(suspended).isSameAs(outer)
        assertThat(LogContext.isActive()).isFalse()

        val inner = LogContext.instance()
        inner.setPrefix("inner")
        LogContext.enter()
        inner.log("内层日志")
        inner.flush()
        LogContext.exit()

        LogContext.resume()
        assertThat(LogContext.instance()).isSameAs(outer)
        assertThat(LogContext.isActive()).isTrue()
    }
}
