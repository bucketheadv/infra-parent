package io.infra.structure.core.support.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Excel操作工具类，封装Apache POI的Workbook操作
 * 支持读写Excel文件，采用面向对象设计
 * <p>
 * 使用示例：
 * <pre>
 * try (Excel excel = Excel.create("output.xlsx")) {
 *     excel.sheet("员工表")
 *          .headers(Map.of("name", "姓名", "age", "年龄"))
 *          .write(dataList);
 *     excel.flush();
 * }
 * </pre>
 *
 * @author sven
 * Created on 2025/10/23 14:30
 */
@Slf4j
public class Excel implements Closeable {

    private static final String EXCEL_XLS = ".xls";
    private static final String EXCEL_XLSX = ".xlsx";

    private final Workbook workbook;
    private final String filePath;
    private final Map<String, ExcelSheet> sheetCache;
    private final CellStyle headerStyle;
    private final CellStyle dataStyle;

    private Excel(String filePath, boolean createNew) {
        this.filePath = filePath;
        this.sheetCache = new LinkedHashMap<>();

        if (createNew) {
            this.workbook = createWorkbook(filePath);
        } else {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IllegalArgumentException("文件不存在: " + filePath);
            }
            try {
                this.workbook = loadWorkbook(file);
            } catch (IOException e) {
                throw new RuntimeException("加载Excel文件失败: " + filePath, e);
            }
        }

        this.headerStyle = StyleHelper.createHeaderStyle(workbook);
        this.dataStyle = StyleHelper.createDataStyle(workbook);
    }

    /**
     * 创建新的Excel文件
     */
    public static Excel create(String filePath) {
        return new Excel(filePath, true);
    }

    /**
     * 打开已存在的Excel文件
     */
    public static Excel open(String filePath) {
        return new Excel(filePath, false);
    }

    /**
     * 获取或创建Sheet
     */
    public ExcelSheet sheet(String sheetName) {
        if (sheetCache.containsKey(sheetName)) {
            return sheetCache.get(sheetName);
        }

        org.apache.poi.ss.usermodel.Sheet poiSheet = workbook.getSheet(sheetName);
        if (poiSheet == null) {
            poiSheet = workbook.createSheet(sheetName);
        }

        ExcelSheet sheet = new ExcelSheet(poiSheet, this);
        sheetCache.put(sheetName, sheet);
        return sheet;
    }

    /**
     * 根据索引获取Sheet
     */
    public ExcelSheet sheetAt(int index) {
        if (index < 0 || index >= workbook.getNumberOfSheets()) {
            throw new IndexOutOfBoundsException("Sheet索引越界: " + index);
        }

        org.apache.poi.ss.usermodel.Sheet poiSheet = workbook.getSheetAt(index);
        String sheetName = poiSheet.getSheetName();

        if (sheetCache.containsKey(sheetName)) {
            return sheetCache.get(sheetName);
        }

        ExcelSheet sheet = new ExcelSheet(poiSheet, this);
        sheetCache.put(sheetName, sheet);
        return sheet;
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
     * 遍历所有Sheet
     */
    public Iterator<ExcelSheet> iterator() {
        return new Iterator<ExcelSheet>() {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return currentIndex < workbook.getNumberOfSheets();
            }

            @Override
            public ExcelSheet next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return sheetAt(currentIndex++);
            }
        };
    }

    /**
     * 读取所有Sheet的数据
     */
    public Map<String, List<Map<String, Object>>> readAll() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            ExcelSheet sheet = sheetAt(i);
            result.put(sheet.name(), sheet.read());
        }
        return result;
    }

    /**
     * 刷新并写入文件
     */
    public void flush() {
        try (FileOutputStream fos = new FileOutputStream(ensureParentDir())) {
            workbook.write(fos);
        } catch (Exception e) {
            log.error("写入Excel文件失败: {}", filePath, e);
            throw new RuntimeException("写入Excel文件失败", e);
        }
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

    private File ensureParentDir() {
        File file = new File(filePath);
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        return file;
    }

    private static Workbook createWorkbook(String filePath) {
        if (filePath.endsWith(EXCEL_XLS)) {
            return new HSSFWorkbook();
        } else if (filePath.endsWith(EXCEL_XLSX)) {
            return new XSSFWorkbook();
        } else {
            throw new IllegalArgumentException("文件格式不支持，仅支持.xls和.xlsx格式");
        }
    }

    private static Workbook loadWorkbook(File file) throws IOException {
        String fileName = file.getName();
        try (FileInputStream fis = new FileInputStream(file)) {
            if (fileName.endsWith(EXCEL_XLS)) {
                return new HSSFWorkbook(fis);
            } else if (fileName.endsWith(EXCEL_XLSX)) {
                return new XSSFWorkbook(fis);
            } else {
                throw new IllegalArgumentException("文件格式不支持，仅支持.xls和.xlsx格式");
            }
        }
    }

    /**
     * ExcelSheet - Sheet操作封装类
     */
    public class ExcelSheet {
        private final org.apache.poi.ss.usermodel.Sheet poiSheet;
        private final Excel excel;
        private Map<String, String> customHeaders;

        private ExcelSheet(org.apache.poi.ss.usermodel.Sheet poiSheet, Excel excel) {
            this.poiSheet = poiSheet;
            this.excel = excel;
        }

        /**
         * 获取Sheet名称
         */
        public String name() {
            return poiSheet.getSheetName();
        }

        /**
         * 设置自定义表头
         */
        public ExcelSheet headers(Map<String, String> headers) {
            this.customHeaders = headers;
            return this;
        }

        /**
         * 写入Map数据
         */
        public ExcelSheet write(List<Map<String, Object>> dataList) {
            if (dataList == null || dataList.isEmpty()) {
                return this;
            }

            List<String> fieldNames = new ArrayList<>(dataList.getFirst().keySet());
            writeHeader(fieldNames);
            writeRows(dataList, fieldNames, getNextRowNum());
            autoSizeColumns(fieldNames.size());

            return this;
        }

        /**
         * 写入Bean对象列表（自动识别@ExcelColumn注解）
         */
        public <T> ExcelSheet write(List<T> dataList, Class<T> clazz) {
            if (dataList == null || dataList.isEmpty()) {
                return this;
            }

            List<Map<String, Object>> mapList = BeanConverter.beansToMaps(dataList, clazz);
            return write(mapList);
        }

        /**
         * 追加Map数据
         */
        public ExcelSheet append(List<Map<String, Object>> dataList) {
            if (dataList == null || dataList.isEmpty()) {
                return this;
            }

            if (poiSheet.getLastRowNum() == -1 || poiSheet.getPhysicalNumberOfRows() == 0) {
                return write(dataList);
            }

            List<String> headers = readHeaders();
            writeRows(dataList, headers, poiSheet.getLastRowNum() + 1);

            return this;
        }

        /**
         * 读取Sheet数据为Map列表
         */
        public List<Map<String, Object>> read() {
            List<Map<String, Object>> result = new ArrayList<>();
            Row headerRow = poiSheet.getRow(0);
            if (headerRow == null) {
                return result;
            }

            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(CellHelper.getValue(headerRow.getCell(i)));
            }

            for (int i = 1; i <= poiSheet.getLastRowNum(); i++) {
                Row row = poiSheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    rowData.put(headers.get(j), CellHelper.getValue(row.getCell(j)));
                }
                result.add(rowData);
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
         * @param fieldMapping 字段映射（key: Excel表头名, value: Bean字段名）
         */
        public <T> List<T> read(Class<T> clazz, Map<String, String> fieldMapping) {
            List<Map<String, Object>> mapList = read();
            return BeanConverter.mapsToBeans(mapList, clazz, fieldMapping);
        }

        /**
         * 清空Sheet数据（保留表头）
         */
        public ExcelSheet clear() {
            int lastRowNum = poiSheet.getLastRowNum();
            for (int i = lastRowNum; i > 0; i--) {
                Row row = poiSheet.getRow(i);
                if (row != null) {
                    poiSheet.removeRow(row);
                }
            }
            return this;
        }

        /**
         * 获取数据行数（不包括表头）
         */
        public int rowCount() {
            return Math.max(0, poiSheet.getLastRowNum());
        }

        /**
         * 返回Excel实例，支持链式调用
         */
        public Excel done() {
            return excel;
        }

        private void writeHeader(List<String> fieldNames) {
            Row headerRow = poiSheet.getRow(0);
            if (headerRow == null) {
                headerRow = poiSheet.createRow(0);
            }

            for (int i = 0; i < fieldNames.size(); i++) {
                Cell cell = headerRow.createCell(i);
                String fieldName = fieldNames.get(i);
                String headerName = (customHeaders != null && customHeaders.containsKey(fieldName))
                        ? customHeaders.get(fieldName)
                        : fieldName;
                CellHelper.setValue(cell, headerName);
                cell.setCellStyle(headerStyle);
            }
        }

        private void writeRows(List<Map<String, Object>> dataList, List<String> fieldNames, int startRow) {
            for (int i = 0; i < dataList.size(); i++) {
                Row row = poiSheet.createRow(startRow + i);
                Map<String, Object> rowData = dataList.get(i);
                for (int j = 0; j < fieldNames.size(); j++) {
                    Cell cell = row.createCell(j);
                    CellHelper.setValue(cell, rowData.get(fieldNames.get(j)));
                    cell.setCellStyle(dataStyle);
                }
            }
        }

        private List<String> readHeaders() {
            Row headerRow = poiSheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(CellHelper.getValue(headerRow.getCell(i)));
            }
            return headers;
        }

        private int getNextRowNum() {
            int startRow = poiSheet.getLastRowNum() + 1;
            return startRow == 0 ? 1 : startRow;
        }

        private void autoSizeColumns(int columnCount) {
            for (int i = 0; i < columnCount; i++) {
                try {
                    poiSheet.autoSizeColumn(i);
                    poiSheet.setColumnWidth(i, poiSheet.getColumnWidth(i) + 1024);
                } catch (Exception e) {
                    log.debug("自动调整列宽失败: {}", i);
                }
            }
        }
    }

    /**
     * 单元格操作辅助类
     */
    static class CellHelper {
        static String getValue(Cell cell) {
            if (cell == null) {
                return "";
            }

            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        return sdf.format(cell.getDateCellValue());
                    } else {
                        return new BigDecimal(cell.getNumericCellValue()).toPlainString();
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return new BigDecimal(cell.getNumericCellValue()).toPlainString();
                    } catch (Exception e) {
                        return cell.getStringCellValue();
                    }
                case BLANK:
                default:
                    return "";
            }
        }

        static void setValue(Cell cell, Object value) {
            switch (value) {
                case null -> cell.setCellValue("");
                case Number number -> cell.setCellValue(number.doubleValue());
                case Date ignored -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    cell.setCellValue(sdf.format(value));
                }
                case Boolean b -> cell.setCellValue(b);
                default -> cell.setCellValue(value.toString());
            }

        }
    }

    /**
     * 样式辅助类
     */
    static class StyleHelper {
        static CellStyle createHeaderStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(style);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);

            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            style.setFont(font);

            return style;
        }

        static CellStyle createDataStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            setBorders(style);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static void setBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }

    /**
     * Bean转换辅助类
     */
    static class BeanConverter {
        static <T> List<Map<String, Object>> beansToMaps(List<T> dataList, Class<T> clazz) {
            List<Map<String, Object>> mapList = new ArrayList<>();
            try {
                Field[] fields = clazz.getDeclaredFields();

                for (T obj : dataList) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (Field field : fields) {
                        field.setAccessible(true);
                        // 如果字段有@ExcelColumn注解，使用注解的值作为key；否则使用字段名
                        String key = getColumnName(field);
                        map.put(key, field.get(obj));
                    }
                    mapList.add(map);
                }
            } catch (Exception e) {
                log.error("转换Bean为Map失败", e);
                throw new RuntimeException("转换Bean为Map失败", e);
            }
            return mapList;
        }

        static <T> List<T> mapsToBeans(List<Map<String, Object>> mapList, Class<T> clazz, Map<String, String> fieldMapping) {
            List<T> result = new ArrayList<>();
            try {
                Field[] fields = clazz.getDeclaredFields();
                // 构建字段映射表：列名 -> Field
                Map<String, Field> columnToField = new HashMap<>();
                for (Field field : fields) {
                    field.setAccessible(true);
                    // 优先使用@ExcelColumn注解的值，其次使用字段名
                    String columnName = getColumnName(field);
                    columnToField.put(columnName, field);
                    // 同时保留字段名映射，兼容无注解的情况
                    if (!columnName.equals(field.getName())) {
                        columnToField.put(field.getName(), field);
                    }
                }

                for (Map<String, Object> map : mapList) {
                    T obj = clazz.getDeclaredConstructor().newInstance();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        try {
                            String key = entry.getKey();
                            Field field = null;

                            // 1. 如果有外部传入的字段映射，优先使用
                            if (fieldMapping != null && fieldMapping.containsKey(key)) {
                                String mappedFieldName = fieldMapping.get(key);
                                field = columnToField.get(mappedFieldName);
                            }
                            
                            // 2. 直接通过列名查找（支持@Column注解）
                            if (field == null) {
                                field = columnToField.get(key);
                            }

                            if (field != null) {
                                Object value = convertValue(entry.getValue(), field.getType());
                                field.set(obj, value);
                            } else {
                                log.debug("字段不存在: {}", key);
                            }
                        } catch (Exception e) {
                            log.debug("设置字段失败: {}", entry.getKey(), e);
                        }
                    }
                    result.add(obj);
                }
            } catch (Exception e) {
                log.error("转换Map为Bean失败", e);
                throw new RuntimeException("转换Map为Bean失败", e);
            }
            return result;
        }

        /**
         * 获取字段的列名（优先使用@ExcelColumn注解）
         */
        private static String getColumnName(Field field) {
            ExcelColumn column = field.getAnnotation(ExcelColumn.class);
            return column != null ? column.value() : field.getName();
        }

        private static Object convertValue(Object value, Class<?> targetType) {
            if (value == null || value.toString().trim().isEmpty()) {
                return null;
            }

            String strValue = value.toString();
            try {
                if (targetType == String.class) {
                    return strValue;
                } else if (targetType == Integer.class || targetType == int.class) {
                    return Integer.parseInt(strValue);
                } else if (targetType == Long.class || targetType == long.class) {
                    return Long.parseLong(strValue);
                } else if (targetType == Double.class || targetType == double.class) {
                    return Double.parseDouble(strValue);
                } else if (targetType == Float.class || targetType == float.class) {
                    return Float.parseFloat(strValue);
                } else if (targetType == Boolean.class || targetType == boolean.class) {
                    return Boolean.parseBoolean(strValue);
                } else if (targetType == BigDecimal.class) {
                    return new BigDecimal(strValue);
                } else {
                    return value;
                }
            } catch (Exception e) {
                log.warn("类型转换失败: {} -> {}", value, targetType.getSimpleName());
                return null;
            }
        }
    }
}

