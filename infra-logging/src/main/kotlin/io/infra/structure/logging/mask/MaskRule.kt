package io.infra.structure.logging.mask

/**
 * 字段脱敏规则，定义一类敏感字段的字段别名与对应的掩码函数。
 *
 * 字段别名匹配不区分大小写，支持 `field=value`、`field: value`、`"field":"value"` 等常见日志格式。
 *
 * @author sven
 */
internal data class MaskRule(
    /** 字段别名集合，例如 `password`、`idCard`、`mobile`。 */
    val fieldNames: Set<String>,
    /** 掩码函数，输入原始值，输出脱敏后的值。 */
    val mask: (String) -> String
)