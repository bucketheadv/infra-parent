package io.infra.structure.core.support.document.excel;

import io.infra.structure.core.support.document.common.BeanConverter;
import io.infra.structure.core.support.document.common.CellHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

/**
 * Excel写入工具类
 * 支持创建和写入Excel文件
 * <p>
 * 使用示例：
 * <pre>
 * try (ExcelWriter writer = ExcelWriter.create("output.xlsx")) {
 *     writer.sheet("员工表")
 *           .headers(Map.of("name", "姓名", "age", "年龄"))
 *           .write(dataList);
 *     writer.flush();
 * }
 * </pre>
 *
 * @author sven
 * Created on 2025/10/23
 */
@Slf4j
public class ExcelWriter implements Closeable {

    private static final String EXCEL_XLS = ".xls";
    private static final String EXCEL_XLSX = ".xlsx";

    private final Workbook workbook;
    private final String filePath;
    private final Map<String, SheetWriter> sheetCache;
    private final CellStyle headerStyle;
    private final CellStyle dataStyle;

    private ExcelWriter(String filePath, boolean createNew) {
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

        this.headerStyle = createHeaderStyle(workbook);
        this.dataStyle = createDataStyle(workbook);
    }

    /**
     * 创建新的Excel文件
     */
    public static ExcelWriter create(String filePath) {
        return new ExcelWriter(filePath, true);
    }

    /**
     * 打开已存在的Excel文件进行编辑
     */
    public static ExcelWriter open(String filePath) {
        return new ExcelWriter(filePath, false);
    }

    /**
     * 获取或创建Sheet
     */
    public SheetWriter sheet(String sheetName) {
        if (sheetCache.containsKey(sheetName)) {
            return sheetCache.get(sheetName);
        }

        Sheet poiSheet = workbook.getSheet(sheetName);
        if (poiSheet == null) {
            poiSheet = workbook.createSheet(sheetName);
        }

        SheetWriter sheet = new SheetWriter(poiSheet, this);
        sheetCache.put(sheetName, sheet);
        return sheet;
    }

    /**
     * 根据索引获取Sheet
     */
    public SheetWriter sheetAt(int index) {
        if (index < 0 || index >= workbook.getNumberOfSheets()) {
            throw new IndexOutOfBoundsException("Sheet索引越界: " + index);
        }

        Sheet poiSheet = workbook.getSheetAt(index);
        String sheetName = poiSheet.getSheetName();

        if (sheetCache.containsKey(sheetName)) {
            return sheetCache.get(sheetName);
        }

        SheetWriter sheet = new SheetWriter(poiSheet, this);
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

    private static CellStyle createHeaderStyle(Workbook workbook) {
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

    private static CellStyle createDataStyle(Workbook workbook) {
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

    /**
     * Sheet写入器
     */
    public class SheetWriter {
        private final Sheet sheet;
        private final ExcelWriter writer;
        private Map<String, String> customHeaders;

        private SheetWriter(Sheet sheet, ExcelWriter writer) {
            this.sheet = sheet;
            this.writer = writer;
        }

        /**
         * 获取Sheet名称
         */
        public String name() {
            return sheet.getSheetName();
        }

        /**
         * 设置自定义表头
         */
        public SheetWriter headers(Map<String, String> headers) {
            this.customHeaders = headers;
            return this;
        }

        /**
         * 写入数据（支持Map或Bean对象列表，自动识别@ExcelColumn注解）
         */
        public <T> SheetWriter write(List<T> dataList) {
            if (dataList == null || dataList.isEmpty()) {
                return this;
            }

            List<Map<String, Object>> mapList = toMapList(dataList);
            List<String> fieldNames = new ArrayList<>(mapList.getFirst().keySet());
            writeHeader(fieldNames);
            writeRows(mapList, fieldNames, getNextRowNum());
            autoSizeColumns(fieldNames.size());

            return this;
        }

        /**
         * 追加数据（支持Map或Bean对象列表）
         */
        public <T> SheetWriter append(List<T> dataList) {
            if (dataList == null || dataList.isEmpty()) {
                return this;
            }

            if (sheet.getLastRowNum() == -1 || sheet.getPhysicalNumberOfRows() == 0) {
                return write(dataList);
            }

            List<Map<String, Object>> mapList = toMapList(dataList);
            List<String> headers = readHeaders();
            writeRows(mapList, headers, sheet.getLastRowNum() + 1);

            return this;
        }

        /**
         * 清空Sheet数据（保留表头）
         */
        public SheetWriter clear() {
            int lastRowNum = sheet.getLastRowNum();
            for (int i = lastRowNum; i > 0; i--) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    sheet.removeRow(row);
                }
            }
            return this;
        }

        /**
         * 获取数据行数（不包括表头）
         */
        public int rowCount() {
            return Math.max(0, sheet.getLastRowNum());
        }

        /**
         * 返回ExcelWriter实例，支持链式调用
         */
        public ExcelWriter done() {
            return writer;
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

        private void writeHeader(List<String> fieldNames) {
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                headerRow = sheet.createRow(0);
            }

            for (int i = 0; i < fieldNames.size(); i++) {
                Cell cell = headerRow.createCell(i);
                String fieldName = fieldNames.get(i);
                String headerName = (customHeaders != null && customHeaders.containsKey(fieldName))
                        ? customHeaders.get(fieldName)
                        : fieldName;
                setCellValue(cell, headerName);
                cell.setCellStyle(headerStyle);
            }
        }

        private void writeRows(List<Map<String, Object>> dataList, List<String> fieldNames, int startRow) {
            for (int i = 0; i < dataList.size(); i++) {
                Row row = sheet.createRow(startRow + i);
                Map<String, Object> rowData = dataList.get(i);
                for (int j = 0; j < fieldNames.size(); j++) {
                    Cell cell = row.createCell(j);
                    setCellValue(cell, rowData.get(fieldNames.get(j)));
                    cell.setCellStyle(dataStyle);
                }
            }
        }

        private List<String> readHeaders() {
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(getCellValue(headerRow.getCell(i)));
            }
            return headers;
        }

        private int getNextRowNum() {
            int startRow = sheet.getLastRowNum() + 1;
            return startRow == 0 ? 1 : startRow;
        }

        private void autoSizeColumns(int columnCount) {
            for (int i = 0; i < columnCount; i++) {
                try {
                    sheet.autoSizeColumn(i);
                    sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1024);
                } catch (Exception e) {
                    log.debug("自动调整列宽失败: {}", i);
                }
            }
        }

        private String getCellValue(Cell cell) {
            return CellHelper.getValue(cell);
        }

        private void setCellValue(Cell cell, Object value) {
            CellHelper.setValue(cell, value);
        }
    }
}

