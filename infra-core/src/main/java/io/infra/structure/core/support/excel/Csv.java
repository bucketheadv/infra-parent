package io.infra.structure.core.support.excel;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

/**
 * CSV文件操作工具类
 * 支持读写CSV文件，采用面向对象设计
 * <p>
 * 使用示例：
 * <pre>
 * Csv csv = Csv.create("output.csv");
 * csv.headers(Map.of("name", "姓名", "age", "年龄"))
 *    .write(dataList);
 * </pre>
 *
 * @author sven
 * Created on 2025/10/23 16:00
 */
@Slf4j
public class Csv {

    private static final String CSV_SEPARATOR = ",";
    private static final String CSV_QUOTE = "\"";
    private static final String DEFAULT_CHARSET = "UTF-8";

    private final String filePath;
    private Map<String, String> customHeaders;
    private List<String> fieldNames;

    private Csv(String filePath) {
        this.filePath = filePath;
    }

    /**
     * 创建新的CSV文件
     */
    public static Csv create(String filePath) {
        return new Csv(filePath);
    }

    /**
     * 打开已存在的CSV文件
     */
    public static Csv open(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        return new Csv(filePath);
    }

    /**
     * 设置自定义表头
     */
    public Csv headers(Map<String, String> headers) {
        this.customHeaders = headers;
        return this;
    }

    /**
     * 写入Map数据
     */
    public Csv write(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return this;
        }

        this.fieldNames = new ArrayList<>(dataList.getFirst().keySet());

        try (BufferedWriter writer = createWriter(false)) {
            writeHeaderLine(writer);
            writeDataLines(writer, dataList);
        } catch (Exception e) {
            log.error("写入CSV文件失败: {}", filePath, e);
            throw new RuntimeException("写入CSV文件失败", e);
        }

        return this;
    }

    /**
     * 写入Bean对象列表（自动识别@ExcelColumn注解）
     */
    public <T> Csv write(List<T> dataList, Class<T> clazz) {
        if (dataList == null || dataList.isEmpty()) {
            return this;
        }

        List<Map<String, Object>> mapList = Excel.BeanConverter.beansToMaps(dataList, clazz);
        return write(mapList);
    }

    /**
     * 追加数据
     */
    public Csv append(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return this;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return write(dataList);
        }

        try {
            List<String> headers = readHeadersFromFile();
            try (BufferedWriter writer = createWriter(true)) {
                writeDataLines(writer, dataList, headers);
            }
        } catch (Exception e) {
            log.error("追加CSV文件失败: {}", filePath, e);
            throw new RuntimeException("追加CSV文件失败", e);
        }

        return this;
    }

    /**
     * 读取CSV数据为Map列表
     */
    public List<Map<String, Object>> read() {
        List<Map<String, Object>> result = new ArrayList<>();

        try (BufferedReader reader = createReader()) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return result;
            }

            List<String> headers = parseLine(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> values = parseLine(line);
                Map<String, Object> rowData = new LinkedHashMap<>();

                for (int i = 0; i < headers.size(); i++) {
                    String value = i < values.size() ? values.get(i) : "";
                    rowData.put(headers.get(i), value);
                }
                result.add(rowData);
            }
        } catch (Exception e) {
            log.error("读取CSV文件失败: {}", filePath, e);
            throw new RuntimeException("读取CSV文件失败", e);
        }

        return result;
    }

    /**
     * 读取为Bean对象列表（自动识别@ExcelColumn注解）
     */
    public <T> List<T> read(Class<T> clazz) {
        List<Map<String, Object>> mapList = read();
        return Excel.BeanConverter.mapsToBeans(mapList, clazz, null);
    }

    /**
     * 读取为Bean对象列表（使用自定义字段映射）
     * 
     * @param clazz Bean类型
     * @param fieldMapping 字段映射（key: CSV表头名, value: Bean字段名）
     */
    public <T> List<T> read(Class<T> clazz, Map<String, String> fieldMapping) {
        List<Map<String, Object>> mapList = read();
        return Excel.BeanConverter.mapsToBeans(mapList, clazz, fieldMapping);
    }


    private BufferedWriter createWriter(boolean append) throws IOException {
        File file = new File(filePath);
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, append), DEFAULT_CHARSET));
    }

    private BufferedReader createReader() throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(filePath), DEFAULT_CHARSET));
    }

    private void writeHeaderLine(BufferedWriter writer) throws IOException {
        List<String> headerNames = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String headerName = (customHeaders != null && customHeaders.containsKey(fieldName))
                    ? customHeaders.get(fieldName)
                    : fieldName;
            headerNames.add(headerName);
        }
        writer.write(formatLine(headerNames));
        writer.newLine();
    }

    private void writeDataLines(BufferedWriter writer, List<Map<String, Object>> dataList) throws IOException {
        writeDataLines(writer, dataList, fieldNames);
    }

    private void writeDataLines(BufferedWriter writer, List<Map<String, Object>> dataList, List<String> headers) throws IOException {
        for (Map<String, Object> rowData : dataList) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object value = rowData.get(header);
                values.add(value == null ? "" : value.toString());
            }
            writer.write(formatLine(values));
            writer.newLine();
        }
    }

    private List<String> readHeadersFromFile() throws IOException {
        try (BufferedReader reader = createReader()) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("CSV文件为空");
            }
            return parseLine(headerLine);
        }
    }

    /**
     * 解析CSV行，处理引号和逗号
     */
    private List<String> parseLine(String line) {
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
    private String formatLine(List<String> values) {
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
    private String formatValue(String value) {
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

