package org.infra.structure.core.startup;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * @author sven
 * Created on 2025/1/12 14:46
 */
@Data
@Configuration
@ConfigurationProperties(prefix = StartupProperties.AUTOLOAD_PREFIX)
public class StartupProperties {
    public static final String AUTOLOAD_PREFIX = "infra.structure.startup";

    private Set<String> includes;

    private Set<String> excludes;
}
