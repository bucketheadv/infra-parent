package io.infra.structure.core.support.document.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV格式处理辅助类
 * 提供CSV行的读取、解析和格式化功能
 *
 * @author sven
 * Created on 2025/10/23
 */
public class CsvHelper {

    private static final String CSV_SEPARATOR = ",";
    private static final String CSV_QUOTE = "\"";

    /**
     * 读取CSV行，处理引号内的换行符
     */
    public static String readCsvLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        boolean inQuotes = false;
        int c;

        while ((c = reader.read()) != -1) {
            char ch = (char) c;

            if (ch == '"') {
                line.append(ch);
                // 检查是否是转义的引号
                reader.mark(1);
                int next = reader.read();
                if (next == '"') {
                    line.append('"');
                } else {
                    if (next != -1) {
                        reader.reset();
                    }
                    inQuotes = !inQuotes;
                }
            } else if (ch == '\n' && !inQuotes) {
                // 行结束（不在引号内）
                break;
            } else if (ch == '\r') {
                // 处理\r和\r\n的情况
                reader.mark(1);
                int next = reader.read();
                if (next == '\n' && !inQuotes) {
                    break;
                } else {
                    if (next != -1) {
                        reader.reset();
                    }
                    if (!inQuotes) {
                        break;
                    }
                    line.append(ch);
                }
            } else {
                line.append(ch);
            }
        }

        return !line.isEmpty() || c != -1 ? line.toString() : null;
    }

    /**
     * 解析CSV行，处理引号和逗号
     */
    public static List<String> parseLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }

        result.add(field.toString());
        return result;
    }

    /**
     * 格式化CSV行，处理引号和逗号
     */
    public static String formatLine(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(CSV_SEPARATOR);
            }
            sb.append(formatValue(values.get(i)));
        }
        return sb.toString();
    }

    /**
     * 格式化CSV值，如果包含逗号、引号或换行符，则用引号包裹
     */
    public static String formatValue(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(CSV_SEPARATOR) || value.contains(CSV_QUOTE) || value.contains("\n") || value.contains("\r")) {
            String escaped = value.replace(CSV_QUOTE, CSV_QUOTE + CSV_QUOTE);
            return CSV_QUOTE + escaped + CSV_QUOTE;
        }

        return value;
    }
}

