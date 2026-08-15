package io.infra.structure.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * PolyLog AOP 传播策略集成测试。
 * 通过 ListAppender 捕获 LogContext 的日志输出，验证：
 * - REQUIRED（默认）：嵌套方法复用外层日志流，仅最外层 flush 一次
 * - REQUIRES_NEW：新开独立日志流并立即 flush，随后恢复外层日志流
 * - 异常场景：最外层以 ERROR 级别 flush
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [PolyLogTestConfiguration::class])
class PolyLogAspectTest {

    @Autowired
    private lateinit var component: PolyLogTestComponent

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
    fun `最外层 PolyLog 方法 flush 一次并输出收集的日志`() {
        component.outer()

        assertThat(appender.list).hasSize(1)
        val message = appender.list[0].formattedMessage
        assertThat(message).contains("[outer]")
        assertThat(message).contains("外层开始")
        assertThat(message).contains("外层信息")
        assertThat(message).matches(".*\\(PolyLogTestComponent\\.kt:\\d+\\) 外层开始.*")
    }

    @Test
    fun `REQUIRED 嵌套方法复用外层日志流`() {
        component.outerCallsInner(component)

        // 外层 flush 一次，内层不再单独 flush
        assertThat(appender.list).hasSize(1)
        val message = appender.list[0].formattedMessage
        assertThat(message).contains("[outer]")
        assertThat(message).contains("外层-前")
        assertThat(message).contains("内层日志")
        assertThat(message).contains("外层-后")
        assertThat(message).matches(".*\\(PolyLogTestComponent\\.kt:\\d+\\) 外层-前 \\|.*")
        assertThat(message).matches(".*\\[inner\\] \\(PolyLogTestComponent\\.kt:\\d+\\) 内层日志.*")
        assertThat(message).matches(".*\\| \\(PolyLogTestComponent\\.kt:\\d+\\) 外层-后.*")
    }

    @Test
    fun `REQUIRES_NEW 新开独立日志流并恢复外层`() {
        component.outerCallsInnerNew(component)

        // 内层 REQUIRES_NEW 单独 flush 一条，外层再 flush 一条
        assertThat(appender.list).hasSize(2)
        val innerMessage = appender.list[0].formattedMessage
        assertThat(innerMessage).matches("\\[inner-new\\] \\(PolyLogTestComponent\\.kt:\\d+\\) 新开日志流")

        val outerMessage = appender.list[1].formattedMessage
        assertThat(outerMessage).contains("[outer]")
        assertThat(outerMessage).contains("外层-前")
        assertThat(outerMessage).contains("外层-后")
        assertThat(outerMessage).doesNotContain("新开日志流")
    }

    @Test
    fun `异常时最外层以 ERROR 级别 flush`() {
        assertThatThrownBy { component.outerThrows() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("模拟异常")

        assertThat(appender.list).hasSize(1)
        assertThat(appender.list[0].level.toString()).isEqualTo("ERROR")
        assertThat(appender.list[0].formattedMessage).contains("异常前的日志")
    }

    @Test
    fun `REQUIRES_NEW 在无外层上下文时也能独立工作`() {
        component.innerNew()

        assertThat(appender.list).hasSize(1)
        assertThat(appender.list[0].formattedMessage).matches("\\[inner-new\\] \\(PolyLogTestComponent\\.kt:\\d+\\) 新开日志流")
    }
}
