package io.infra.structure.script.properties

import io.infra.structure.script.constants.prefix
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * @author liuqinglin
 * Date: 2025/5/31 17:43
 */
@Configuration
@ConfigurationProperties(prefix = prefix)
data class ScriptProperties(
    var enabled: Boolean = true,
)