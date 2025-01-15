package org.infra.structure.core.startup;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * @author sven
 * Created on 2025/1/12 14:46
 */
@Data
@Configuration
@ConfigurationProperties(prefix = StartupProperties.AUTOLOAD_PREFIX)
public class StartupProperties {
    /**
     * 配置前缀
     */
    public static final String AUTOLOAD_PREFIX = "infra.structure.startup";

    /**
     * bean 加载路径白名单
     */
    private Set<String> includes = new HashSet<>();

    /**
     * bean 加载路径黑名单
     */
    private Set<String> excludes = new HashSet<>();
}
