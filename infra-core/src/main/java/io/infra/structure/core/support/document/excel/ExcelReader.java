package io.infra.structure.core.support.document.excel;

import io.infra.structure.core.support.document.common.BeanConverter;
import io.infra.structure.core.support.document.common.CellHelper;
import io.infra.structure.core.support.document.common.DataConverter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * Excel读取工具类
 * 支持从本地文件和URL读取Excel文件
 * <p>
 * 使用示例：
 * <pre>
 * // 从本地文件读取
 * try (ExcelReader reader = ExcelReader.open("data.xlsx")) {
 *     List&lt;Map&lt;String, Object&gt;&gt; data = reader.sheet(0).read();
 * }
 *
 * // 从URL读取
 * try (ExcelReader reader = ExcelReader.openUrl("https://example.com/data.xlsx")) {
 *     List&lt;Map&lt;String, Object&gt;&gt; data = reader.sheet("员工表").read();
 * }
 * </pre>
 *
 * @author sven
 * Created on 2025/10/23
 */
@Slf4j
public class ExcelReader implements Closeable {

    private static final String EXCEL_XLS = ".xls";
    private static final String EXCEL_XLSX = ".xlsx";

    private final Workbook workbook;
    @Getter
    private final String source;

    private ExcelReader(Workbook workbook, String source) {
        this.workbook = workbook;
        this.source = source;
    }

    /**
     * 打开本地Excel文件
     */
    public static ExcelReader open(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        try {
            Workbook workbook = loadWorkbookFromFile(file);
            return new ExcelReader(workbook, filePath);
        } catch (IOException e) {
            throw new RuntimeException("加载Excel文件失败: " + filePath, e);
        }
    }

    /**
     * 从URL打开Excel文件
     */
    public static ExcelReader openUrl(String url) {
        try {
            Workbook workbook = loadWorkbookFromUrl(url);
            return new ExcelReader(workbook, url);
        } catch (IOException e) {
            throw new RuntimeException("从URL加载Excel文件失败: " + url, e);
        }
    }

    /**
     * 从InputStream打开Excel文件
     */
    public static ExcelReader open(InputStream inputStream, String fileName) {
        try {
            Workbook workbook = loadWorkbookFromStream(inputStream, fileName);
            return new ExcelReader(workbook, fileName);
        } catch (IOException e) {
            throw new RuntimeException("从InputStream加载Excel文件失败: " + fileName, e);
        }
    }

    /**
     * 根据名称获取Sheet
     */
    public SheetReader sheet(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet不存在: " + sheetName);
        }
        return new SheetReader(sheet);
    }

    /**
     * 根据索引获取Sheet
     */
    public SheetReader sheet(int index) {
        if (index < 0 || index >= workbook.getNumberOfSheets()) {
            throw new IndexOutOfBoundsException("Sheet索引越界: " + index);
        }
        Sheet sheet = workbook.getSheetAt(index);
        return new SheetReader(sheet);
    }

    /**
     * 获取所有Sheet名称
     */
    public List<String> sheetNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            names.add(workbook.getSheetAt(i).getSheetName());
        }
        return names;
    }

    /**
     * 获取Sheet数量
     */
    public int sheetCount() {
        return workbook.getNumberOfSheets();
    }

    /**
     * 读取所有Sheet的数据
     */
    public Map<String, List<Map<String, Object>>> readAll() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            result.put(sheet.getSheetName(), new SheetReader(sheet).read());
        }
        return result;
    }

    /**
     * 关闭资源
     */
    @Override
    public void close() {
        try {
            if (workbook != null) {
                workbook.close();
            }
        } catch (IOException e) {
            log.error("关闭Workbook失败", e);
        }
    }

    /**
     * 从文件加载Workbook
     */
    private static Workbook loadWorkbookFromFile(File file) throws IOException {
        String fileName = file.getName();
        try (FileInputStream fis = new FileInputStream(file)) {
            return loadWorkbookFromStream(fis, fileName);
        }
    }

    /**
     * 从URL加载Workbook
     */
    private static Workbook loadWorkbookFromUrl(String urlString) throws IOException {
        URL url = URI.create(urlString).toURL();
        try (InputStream inputStream = url.openStream()) {
            return loadWorkbookFromStream(inputStream, urlString);
        }
    }

    /**
     * 从InputStream加载Workbook
     */
    private static Workbook loadWorkbookFromStream(InputStream inputStream, String fileName) throws IOException {
        if (fileName.endsWith(EXCEL_XLS)) {
            return new HSSFWorkbook(inputStream);
        } else if (fileName.endsWith(EXCEL_XLSX)) {
            return new XSSFWorkbook(inputStream);
        } else {
            throw new IllegalArgumentException("文件格式不支持，仅支持.xls和.xlsx格式: " + fileName);
        }
    }

    /**
     * Sheet读取器
     */
    public static class SheetReader {
        private final Sheet sheet;

        private SheetReader(Sheet sheet) {
            this.sheet = sheet;
        }

        /**
         * 获取Sheet名称
         */
        public String name() {
            return sheet.getSheetName();
        }

        /**
         * 读取Sheet数据为Map列表（第一行作为表头）
         */
        public List<Map<String, Object>> read() {
            List<Map<String, Object>> result = new ArrayList<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return result;
            }

            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(getCellValue(headerRow.getCell(i)));
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                List<String> values = new ArrayList<>();
                for (int j = 0; j < headers.size(); j++) {
                    values.add(getCellValue(row.getCell(j)));
                }
                result.add(DataConverter.toMap(headers, values));
            }

            return result;
        }

        /**
         * 读取Sheet数据为List列表（无表头，返回原始数据）
         * 
         * @return 二维列表，每行是一个List
         */
        public List<List<String>> readWithoutHeaders() {
            List<List<String>> result = new ArrayList<>();
            
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                List<String> rowData = new ArrayList<>();
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    rowData.add(getCellValue(row.getCell(j)));
                }
                result.add(rowData);
            }

            return result;
        }

        /**
         * 使用自定义表头读取Sheet数据
         * 
         * @param customHeaders 自定义表头列表
         * @return Map列表，使用提供的表头作为key
         */
        public List<Map<String, Object>> read(List<String> customHeaders) {
            DataConverter.validateHeaders(customHeaders);
            List<Map<String, Object>> result = new ArrayList<>();

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                List<String> values = new ArrayList<>();
                for (int j = 0; j < customHeaders.size(); j++) {
                    values.add(getCellValue(row.getCell(j)));
                }
                result.add(DataConverter.toMap(customHeaders, values));
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
         */
        public <T> List<T> read(Class<T> clazz, Map<String, String> fieldMapping) {
            List<Map<String, Object>> mapList = read();
            return BeanConverter.mapsToBeans(mapList, clazz, fieldMapping);
        }

        /**
         * 获取数据行数（不包括表头）
         */
        public int rowCount() {
            return Math.max(0, sheet.getLastRowNum());
        }

        /**
         * 获取单元格值
         */
        private String getCellValue(Cell cell) {
            return CellHelper.getValue(cell);
        }
    }
}

