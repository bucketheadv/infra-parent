package io.infra.structure.logging.converter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

/**
 * @author liuqinglin
 * Date: 2025/4/2 17:33
 */
public class LevelColorConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {
    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.getLevel().toInt()) {
            case Level.ERROR_INT -> "38;2;247;93;92";
            case Level.WARN_INT -> "38;2;175;135;95";
            case Level.INFO_INT -> "38;2;135;175;95";
            case Level.DEBUG_INT -> "38;2;96;135;135";
            case Level.TRACE_INT -> "38;2;95;95;95";
            default -> "38;5;15";
        };
    }
}
