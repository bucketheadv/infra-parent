package io.infra.structure.logging.converter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

/**
 * @author liuqinglin
 * Date: 2025/4/2 17:33
 */
public class ThreadColorConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {
    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return "38;2;96;175;96";
    }
}
