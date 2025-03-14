package io.infra.structure.db.autoconfiguration;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author sven
 * Created on 2025/1/18 20:53
 */
@Configuration
@ComponentScan(basePackages = "io.infra.structure.db")
public class InfraDBAutoConfiguration {
}
