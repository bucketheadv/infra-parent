package io.infra.structure.logging.mask

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.CompositeConverter

/**
 * 日志字段脱敏转换器，用于在日志输出时对敏感字段进行脱敏。
 *
 * 在 logback-spring.xml 中注册 conversionWord 为 `mask`，通过 `%mask(%msg)` 包裹消息输出。
 * 支持以下上下文属性（可在 logback-spring.xml 中通过 `<springProperty>` 注入）：
 * - `log.mask.enabled`：是否启用脱敏，默认 true；
 * - `log.mask.extra-fields`：追加的敏感字段名，逗号分隔，命中时整体掩码。
 *
 * @author sven
 */
class FieldMaskConverter : CompositeConverter<ILoggingEvent>() {

    /** 是否启用脱敏，默认 true。 */
    private var enabled: Boolean = true

    /** 追加的自定义敏感字段名。 */
    private var extraFields: List<String> = emptyList()

    override fun start() {
        super.start()
        val context = context
        enabled = context?.getProperty(PROP_ENABLED)?.toBoolean() ?: true
        extraFields = context?.getProperty(PROP_EXTRA_FIELDS)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    override fun transform(event: ILoggingEvent, `in`: String?): String {
        val message = `in` ?: event.formattedMessage
        return if (enabled) FieldMasker.mask(message, extraFields) else message
    }

    companion object {
        /** 启用开关属性名。 */
        private const val PROP_ENABLED = "log.mask.enabled"

        /** 追加字段属性名。 */
        private const val PROP_EXTRA_FIELDS = "log.mask.extra-fields"
    }
}