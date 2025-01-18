package org.infra.structure.db.autoconfiguration;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author sven
 * Created on 2025/1/18 20:53
 */
@Configuration
@ComponentScan(basePackages = "org.infra.structure.db")
public class InfraDbAutoConfiguration {
}
