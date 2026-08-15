package io.infra.structure.logging.mask

/**
 * 日志字段脱敏器，对日志消息中命中的敏感字段值进行脱敏。
 *
 * 默认覆盖常见敏感字段：
 * - 口令类（password/passwd/pwd/secret/secretKey/apiKey/accessToken/refreshToken/token）整体掩码；
 * - 证件类（idCard/idCardNo/idNumber/certNo/ssn）保留首尾；
 * - 联系方式（mobile/mobileNo/phone/phoneNo/email）按手机号或邮箱规则脱敏。
 *
 * 可通过 [mask] 的 [extraFieldNames] 追加自定义字段，追加字段按整体掩码处理。
 *
 * @author sven
 */
object FieldMasker {

    /** 整体掩码值。 */
    private const val FULL_MASK = "******"

    /** 手机号脱敏保留的前缀位数。 */
    private const val PHONE_PREFIX_LEN = 3

    /** 手机号脱敏保留的后缀位数。 */
    private const val PHONE_SUFFIX_LEN = 4

    /** 证件号脱敏保留的前缀位数。 */
    private const val ID_CARD_PREFIX_LEN = 4

    /** 证件号脱敏保留的后缀位数。 */
    private const val ID_CARD_SUFFIX_LEN = 4

    private val defaultRules: List<MaskRule> = listOf(
        MaskRule(
            fieldNames = setOf(
                "password", "passwd", "pwd",
                "secret", "secretKey", "apiKey",
                "accessToken", "refreshToken", "token"
            ),
            mask = { FULL_MASK }
        ),
        MaskRule(
            fieldNames = setOf("idCard", "idCardNo", "idNumber", "certNo", "ssn"),
            mask = { value -> maskKeepHeadTail(value, ID_CARD_PREFIX_LEN, ID_CARD_SUFFIX_LEN) }
        ),
        MaskRule(
            fieldNames = setOf("mobile", "mobileNo", "phone", "phoneNo"),
            mask = { value -> maskKeepHeadTail(value, PHONE_PREFIX_LEN, PHONE_SUFFIX_LEN) }
        ),
        MaskRule(
            fieldNames = setOf("email"),
            mask = { value -> maskEmail(value) }
        )
    )

    /**
     * 对日志消息执行脱敏。
     *
     * @param message 原始日志消息
     * @param extraFieldNames 追加的自定义敏感字段名，命中时整体掩码；默认空
     * @return 脱敏后的日志消息
     */
    fun mask(message: String, extraFieldNames: List<String> = emptyList()): String {
        if (message.isEmpty()) {
            return message
        }
        var result = message
        val rules = if (extraFieldNames.isEmpty()) {
            defaultRules
        } else {
            defaultRules + MaskRule(extraFieldNames.toSet(), mask = { FULL_MASK })
        }
        for (rule in rules) {
            result = applyRule(result, rule)
        }
        return result
    }

    private fun applyRule(message: String, rule: MaskRule): String {
        val fieldPattern = rule.fieldNames.joinToString("|") { Regex.escape(it) }
        val pattern = Regex(
            "(?i)(?<![\\p{L}\\p{N}_])($fieldPattern)\\s*\\\"?\\s*[:=]\\s*\\\"?([^\\s,\\\"';}\\]|]+)"
        )
        return pattern.replace(message) { match ->
            val rawValue = match.groupValues[2]
            match.value.replace(rawValue, rule.mask(rawValue))
        }
    }

    private fun maskKeepHeadTail(value: String, prefixLen: Int, suffixLen: Int): String {
        if (value.length <= prefixLen + suffixLen) {
            return FULL_MASK
        }
        val middleLen = value.length - prefixLen - suffixLen
        return value.take(prefixLen) + "*".repeat(middleLen) + value.takeLast(suffixLen)
    }

    private fun maskEmail(value: String): String {
        val atIndex = value.indexOf('@')
        if (atIndex <= 1) {
            return FULL_MASK
        }
        val local = value.substring(0, atIndex)
        val domain = value.substring(atIndex)
        val maskedLocal = local.take(1) + "*".repeat(local.length - 1)
        return maskedLocal + domain
    }
}