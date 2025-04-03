package io.infra.logging.converter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * @author liuqinglin
 * Date: 2025/4/3 10:46
 */
public class MsgColorConverter extends CompositeConverter<ILoggingEvent> {
    // 亮绿色
    private static final String BRIGHT_GREEN = "38;2;50;255;50";
    // 亮紫色
    private static final String BRIGHT_MAGENTA = "38;2;255;50;255";
    // 亮青色
    private static final String BRIGHT_CYAN = "38;2;0;255;255";
    // 亮白色
    private static final String BRIGHT_WHITE = "38;2;255;255;255";
    // 重置颜色
    private static final String RESET = "0";

    @Override
    protected String transform(ILoggingEvent event, String in) {
        // 获取原始消息
        String message = event.getFormattedMessage();

        // 处理消息中的变量和关键信息
        StringBuilder coloredMessage = new StringBuilder();
        String[] parts = message.split("\\s+");

        for (String part : parts) {
            if (isVariable(part)) {
                // 变量使用亮白色
                coloredMessage.append("\u001B[").append(BRIGHT_WHITE).append("m")
                        .append(part)
                        .append("\u001B[").append(RESET).append("m");
            } else if (isKeyword(part)) {
                // 关键字使用亮绿色
                coloredMessage.append("\u001B[").append(BRIGHT_GREEN).append("m")
                        .append(part)
                        .append("\u001B[").append(RESET).append("m");
            } else {
                // 普通文本使用默认颜色
                coloredMessage.append(part);
            }
            coloredMessage.append(" ");
        }

        return coloredMessage.toString().trim();
    }

    private boolean isVariable(String text) {
        // 判断是否是变量（这里的规则可以根据实际需求调整）
        return text.matches(".*[0-9]+.*") ||           // 包含数字
                text.matches("^\\[.*") ||           // 以中括号开头
                text.matches(".*]$") ||             // 以中括号结尾
                text.matches("[A-Z_]+") ||              // 全大写字母
                text.startsWith("${") ||                // 环境变量
                text.matches("[a-z]+\\.+[A-Za-z.]+") || // 匹配包名和类名
                text.matches("\\d+\\.\\d+\\.\\d+") ||   // 版本号
                text.matches("\\d+ms") ||               // 时间单位
                text.matches("\\d+MB");                 // 大小单位
    }

    private boolean isKeyword(String text) {
        // 判断是否是关键字（这里的规则可以根据实际需求调整）
        return "activated".equals(text) ||
                "started".equals(text) ||
                "enabled".equals(text) ||
                "initialized".equals(text) ||
                text.startsWith("http://") ||
                text.startsWith("https://");
    }
}