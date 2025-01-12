package org.infra.structure.core.autoconfiguration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author sven
 * Created on 2025/1/12 14:52
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "org.infra.structure.core")
public class InfraCoreAutoConfiguration {
}
