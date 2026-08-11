package io.infra.structure.schedule.core

/** 执行器多地址解析与格式化（逗号 / 分号 / 换行分隔）。 */
object ExecutorAddresses {
    private val separators = Regex("[,;\\n\\r]+")

    /** 解析原始地址配置，去空白并去重（保序）。 */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        raw.split(separators)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { seen += it }
        return seen.toList()
    }

    /** 格式化为存储/展示用的逗号分隔字符串。 */
    fun format(addresses: Collection<String>): String? =
        parse(addresses.joinToString(",")).takeIf { it.isNotEmpty() }?.joinToString(",")
}
