package io.infra.structure.logging.converter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase

/**
 * @author liuqinglin
 * Date: 2025/4/2 17:33
 */
class LevelColorConverter : ForegroundCompositeConverterBase<ILoggingEvent>() {
    override fun getForegroundColorCode(event: ILoggingEvent): String {
        return when (event.level.toInt()) {
            Level.ERROR_INT -> "38;2;247;93;92"
            Level.WARN_INT -> "38;2;175;135;95"
            Level.INFO_INT -> "38;2;135;175;95"
            Level.DEBUG_INT -> "38;2;96;135;135"
            Level.TRACE_INT -> "38;2;95;95;95"
            else -> "38;5;15"
        }
    }
}
