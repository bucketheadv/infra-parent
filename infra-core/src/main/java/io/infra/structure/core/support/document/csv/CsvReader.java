package io.infra.structure.core.support.document.csv;

import io.infra.structure.core.support.document.common.BeanConverter;
import io.infra.structure.core.support.document.common.DataConverter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * CSV读取工具类
 * 支持从本地文件和URL读取CSV文件
 * <p>
 * 使用示例：
 * <pre>
 * // 从本地文件读取
 * CsvReader reader = CsvReader.open("data.csv");
 * List&lt;Map&lt;String, Object&gt;&gt; data = reader.read();
 * 
 * // 从URL读取
 * CsvReader reader = CsvReader.openUrl("https://example.com/data.csv");
 * List&lt;UserBean&gt; users = reader.read(UserBean.class);
 * </pre>
 *
 * @author sven
 * Created on 2025/10/23
 */
@Slf4j
public class CsvReader {

    private static final String DEFAULT_CHARSET = "UTF-8";

    private final String source;
    private final boolean isUrl;

    private CsvReader(String source, boolean isUrl) {
        this.source = source;
        this.isUrl = isUrl;
    }

    /**
     * 打开本地CSV文件
     */
    public static CsvReader open(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        return new CsvReader(filePath, false);
    }

    /**
     * 从URL打开CSV文件
     */
    public static CsvReader openUrl(String url) {
        return new CsvReader(url, true);
    }

    /**
     * 从InputStream打开CSV文件
     */
    public static CsvReader open(InputStream inputStream) {
        try {
            return new CsvReader(readToTempFile(inputStream), false);
        } catch (IOException e) {
            throw new RuntimeException("从InputStream读取CSV文件失败", e);
        }
    }

    /**
     * 读取CSV数据为Map列表（第一行作为表头）
     */
    public List<Map<String, Object>> read() {
        List<Map<String, Object>> result = new ArrayList<>();

        try (BufferedReader reader = createReader()) {
            String headerLine = CsvHelper.readCsvLine(reader);
            if (headerLine == null) {
                return result;
            }

            List<String> headers = CsvHelper.parseLine(headerLine);

            String line;
            while ((line = CsvHelper.readCsvLine(reader)) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> values = CsvHelper.parseLine(line);
                result.add(DataConverter.toMap(headers, values));
            }
        } catch (Exception e) {
            log.error("读取CSV文件失败: {}", source, e);
            throw new RuntimeException("读取CSV文件失败", e);
        }

        return result;
    }

    /**
     * 读取CSV数据为List列表（无表头，返回原始数据）
     * 
     * @return 二维列表，每行是一个List
     */
    public List<List<String>> readWithoutHeaders() {
        List<List<String>> result = new ArrayList<>();

        try (BufferedReader reader = createReader()) {
            String line;
            while ((line = CsvHelper.readCsvLine(reader)) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> values = CsvHelper.parseLine(line);
                result.add(values);
            }
        } catch (Exception e) {
            log.error("读取CSV文件失败: {}", source, e);
            throw new RuntimeException("读取CSV文件失败", e);
        }

        return result;
    }

    /**
     * 使用自定义表头读取CSV数据
     * 
     * @param customHeaders 自定义表头列表
     * @return Map列表，使用提供的表头作为key
     */
    public List<Map<String, Object>> read(List<String> customHeaders) {
        DataConverter.validateHeaders(customHeaders);
        List<Map<String, Object>> result = new ArrayList<>();

        try (BufferedReader reader = createReader()) {
            String line;
            while ((line = CsvHelper.readCsvLine(reader)) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> values = CsvHelper.parseLine(line);
                result.add(DataConverter.toMap(customHeaders, values));
            }
        } catch (Exception e) {
            log.error("读取CSV文件失败: {}", source, e);
            throw new RuntimeException("读取CSV文件失败", e);
        }

        return result;
    }

    /**
     * 读取为Bean对象列表（自动识别@ExcelColumn注解）
     */
    public <T> List<T> read(Class<T> clazz) {
        List<Map<String, Object>> mapList = read();
        return BeanConverter.mapsToBeans(mapList, clazz, null);
    }

    /**
     * 读取为Bean对象列表（使用自定义字段映射）
     * 
     * @param clazz Bean类型
     * @param fieldMapping 字段映射（key: CSV表头名, value: Bean字段名）
     */
    public <T> List<T> read(Class<T> clazz, Map<String, String> fieldMapping) {
        List<Map<String, Object>> mapList = read();
        return BeanConverter.mapsToBeans(mapList, clazz, fieldMapping);
    }

    private BufferedReader createReader() throws IOException {
        if (isUrl) {
            URL url = URI.create(source).toURL();
            return new BufferedReader(new InputStreamReader(url.openStream(), DEFAULT_CHARSET));
        } else {
            return new BufferedReader(new InputStreamReader(new FileInputStream(source), DEFAULT_CHARSET));
        }
    }

    /**
     * 将InputStream内容读取到临时文件
     */
    private static String readToTempFile(InputStream inputStream) throws IOException {
        File tempFile = File.createTempFile("csv_reader_", ".csv");
        tempFile.deleteOnExit();
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        
        return tempFile.getAbsolutePath();
    }
}

