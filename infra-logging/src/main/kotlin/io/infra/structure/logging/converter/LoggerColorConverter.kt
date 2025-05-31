package io.infra.structure.logging.converter

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase

/**
 * @author liuqinglin
 * Date: 2025/4/2 17:33
 */
class LoggerColorConverter : ForegroundCompositeConverterBase<ILoggingEvent>() {
    override fun getForegroundColorCode(event: ILoggingEvent): String = "38;2;96;135;255"
}
