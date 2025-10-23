package io.infra.structure.core.support.document.csv;

import io.infra.structure.core.support.document.common.BeanConverter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

/**
 * CSV写入工具类
 * 支持创建和写入CSV文件
 * <p>
 * 使用示例：
 * <pre>
 * CsvWriter writer = CsvWriter.create("output.csv");
 * writer.headers(Map.of("name", "姓名", "age", "年龄"))
 *       .write(dataList);
 * </pre>
 *
 * @author sven
 * Created on 2025/10/23
 */
@Slf4j
public class CsvWriter {

    private static final String DEFAULT_CHARSET = "UTF-8";

    private final String filePath;
    private Map<String, String> customHeaders;
    private List<String> fieldNames;

    private CsvWriter(String filePath) {
        this.filePath = filePath;
    }

    /**
     * 创建新的CSV文件
     */
    public static CsvWriter create(String filePath) {
        return new CsvWriter(filePath);
    }

    /**
     * 打开已存在的CSV文件进行编辑
     */
    public static CsvWriter open(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        return new CsvWriter(filePath);
    }

    /**
     * 设置自定义表头
     */
    public CsvWriter headers(Map<String, String> headers) {
        this.customHeaders = headers;
        return this;
    }

    /**
     * 写入数据（支持Map或Bean对象列表，自动识别@ExcelColumn注解）
     */
    public <T> CsvWriter write(List<T> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            // 创建空文件
            try (BufferedWriter writer = createWriter(false)) {
                // 空文件，不写入任何内容
            } catch (Exception e) {
                log.error("创建空CSV文件失败: {}", filePath, e);
                throw new RuntimeException("创建空CSV文件失败", e);
            }
            return this;
        }

        List<Map<String, Object>> mapList = toMapList(dataList);
        this.fieldNames = new ArrayList<>(mapList.getFirst().keySet());

        try (BufferedWriter writer = createWriter(false)) {
            writeHeaderLine(writer);
            writeDataLines(writer, mapList);
        } catch (Exception e) {
            log.error("写入CSV文件失败: {}", filePath, e);
            throw new RuntimeException("写入CSV文件失败", e);
        }

        return this;
    }

    /**
     * 追加数据（支持Map或Bean对象列表）
     */
    public <T> CsvWriter append(List<T> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return this;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return write(dataList);
        }

        List<Map<String, Object>> mapList = toMapList(dataList);

        try {
            List<String> headers = readHeadersFromFile();
            try (BufferedWriter writer = createWriter(true)) {
                writeDataLines(writer, mapList, headers);
            }
        } catch (Exception e) {
            log.error("追加CSV文件失败: {}", filePath, e);
            throw new RuntimeException("追加CSV文件失败", e);
        }

        return this;
    }

    /**
     * 将数据列表转换为Map列表（自动识别Map或Bean）
     */
    @SuppressWarnings("unchecked")
    private <T> List<Map<String, Object>> toMapList(List<T> dataList) {
        Object firstElement = dataList.getFirst();
        if (firstElement instanceof Map) {
            return (List<Map<String, Object>>) dataList;
        } else {
            Class<T> clazz = (Class<T>) firstElement.getClass();
            return BeanConverter.beansToMaps(dataList, clazz);
        }
    }

    private BufferedWriter createWriter(boolean append) throws IOException {
        File file = new File(filePath);
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, append), DEFAULT_CHARSET));
    }

    private void writeHeaderLine(BufferedWriter writer) throws IOException {
        List<String> headerNames = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String headerName = (customHeaders != null && customHeaders.containsKey(fieldName))
                    ? customHeaders.get(fieldName)
                    : fieldName;
            headerNames.add(headerName);
        }
        writer.write(CsvHelper.formatLine(headerNames));
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
            writer.write(CsvHelper.formatLine(values));
            writer.newLine();
        }
    }

    private List<String> readHeadersFromFile() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), DEFAULT_CHARSET))) {
            String headerLine = CsvHelper.readCsvLine(reader);
            if (headerLine == null) {
                throw new RuntimeException("CSV文件为空");
            }
            return CsvHelper.parseLine(headerLine);
        }
    }
}

