package io.infra.structure.script.properties

import io.infra.structure.script.constants.configPrefix
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = configPrefix)
class ScriptProperties {
    /**
     * 是否启用脚本功能
     */
    var enabled: Boolean = true
}
