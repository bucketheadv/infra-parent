package io.infra.structure.logging.converter

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.CompositeConverter

/**
 * @author liuqinglin
 * Date: 2025/4/3 10:46
 */
class MsgColorConverter : CompositeConverter<ILoggingEvent>() {
    override fun transform(event: ILoggingEvent, `in`: String?): String {
        // 获取原始消息
        val message = event.formattedMessage

        // 处理消息中的变量和关键信息
        val coloredMessage = StringBuilder()
        val parts = message.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        for (i in parts.indices) {
            val part = parts[i]
            if (isVariable(part)) {
                if (i == 0) {
                    // 如果变量是第一个单词，有时不支持高亮显示，因此不设置高亮
                    coloredMessage.append(part)
                } else {
                    // 变量使用亮白色
                    coloredMessage.append("\u001B[").append(BRIGHT_WHITE).append("m")
                        .append(part)
                        .append("\u001B[").append(RESET).append("m")
                }
            } else if (isKeyword(part)) {
                // 关键字使用亮绿色
                coloredMessage.append("\u001B[").append(BRIGHT_GREEN).append("m")
                    .append(part)
                    .append("\u001B[").append(RESET).append("m")
            } else {
                // 普通文本使用默认颜色
                coloredMessage.append(part)
            }
            coloredMessage.append(" ")
        }

        return coloredMessage.toString().trim { it <= ' ' }
    }

    private fun isVariable(text: String): Boolean {
        // 判断是否是变量（这里的规则可以根据实际需求调整）
        return text.matches(".*[0-9]+.*".toRegex()) ||  // 包含数字
                text.matches("^\\[.*".toRegex()) ||  // 以中括号开头
                text.matches(".*]$".toRegex()) ||  // 以中括号结尾
                text.matches("[A-Z_]+".toRegex()) ||  // 全大写字母
                text.startsWith("\${") ||  // 环境变量
                text.matches("[a-z]+\\.+[A-Za-z.]+".toRegex()) ||  // 匹配包名和类名
                text.matches("\\d+\\.\\d+\\.\\d+".toRegex()) ||  // 版本号
                text.matches("\\d+ms".toRegex()) ||  // 时间单位
                text.matches("\\d+MB".toRegex()) // 大小单位
    }

    private fun isKeyword(text: String): Boolean {
        // 判断是否是关键字（这里的规则可以根据实际需求调整）
        return "activated" == text ||
                "started" == text ||
                "enabled" == text ||
                "initialized" == text ||
                text.startsWith("http://") ||
                text.startsWith("https://")
    }

    companion object {
        // 亮绿色
        private const val BRIGHT_GREEN = "38;2;50;255;50"

        // 亮紫色
        private const val BRIGHT_MAGENTA = "38;2;255;50;255"

        // 亮青色
        private const val BRIGHT_CYAN = "38;2;0;255;255"

        // 亮白色
        private const val BRIGHT_WHITE = "38;2;255;255;255"

        // 重置颜色
        private const val RESET = "0"
    }
}