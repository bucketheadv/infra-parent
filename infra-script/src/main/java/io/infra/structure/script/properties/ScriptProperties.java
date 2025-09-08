package io.infra.structure.script.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author liuqinglin
 * Date: 2025/5/31 17:43
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "infra.script")
public class ScriptProperties {
    /**
     * 是否启用脚本功能
     */
    private boolean enabled = true;
}
