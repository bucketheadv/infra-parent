package io.infra.structure.core.support.document;

import io.infra.structure.core.support.document.csv.CsvReader;
import io.infra.structure.core.support.document.csv.CsvWriter;
import io.infra.structure.core.support.document.excel.ExcelReader;
import io.infra.structure.core.support.document.excel.ExcelWriter;

import java.util.List;
import java.util.Map;

/**
 * URL读写和文件格式转换示例
 * 演示如何从URL读取文件以及Excel与CSV之间的转换
 *
 * @author sven
 * Created on 2025/10/23
 */
public class UrlReadWriteExample {

    public static void main(String[] args) {
        System.out.println("========== Excel和CSV操作示例 ==========\n");
        
        // 1. Excel基本操作
        excelBasicExample();
        
        // 2. CSV基本操作
        csvBasicExample();
        
        // 3. 文件格式转换
        fileConversionExample();
    }

    /**
     * Excel基本操作示例
     */
    private static void excelBasicExample() {
        System.out.println("=== 1. Excel基本操作 ===");
        
        try {
            // 创建测试数据
            List<Map<String, Object>> employees = List.of(
                Map.of("name", "张三", "age", 25, "dept", "技术部"),
                Map.of("name", "李四", "age", 30, "dept", "销售部"),
                Map.of("name", "王五", "age", 28, "dept", "产品部")
            );

            String filePath = System.getProperty("user.home") + "/Downloads/example.xlsx";
            
            // 写入Excel
            try (ExcelWriter writer = ExcelWriter.create(filePath)) {
                writer.sheet("员工表")
                      .headers(Map.of("name", "姓名", "age", "年龄", "dept", "部门"))
                      .write(employees);
                writer.flush();
            }
            System.out.println("✓ Excel文件已创建: " + filePath);

            // 读取Excel
            try (ExcelReader reader = ExcelReader.open(filePath)) {
                System.out.println("  文件包含 " + reader.sheetCount() + " 个Sheet");
                List<Map<String, Object>> data = reader.sheet(0).read();
                System.out.println("  读取到 " + data.size() + " 行数据");
                data.forEach(row -> System.out.println("  " + row));
            }
            
        } catch (Exception e) {
            System.err.println("Excel操作失败: " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * CSV基本操作示例
     */
    private static void csvBasicExample() {
        System.out.println("=== 2. CSV基本操作 ===");
        
        try {
            // 创建测试数据
            List<Map<String, Object>> products = List.of(
                Map.of("name", "产品A", "price", 99.9, "stock", 100),
                Map.of("name", "产品B", "price", 199.9, "stock", 50),
                Map.of("name", "产品C", "price", 299.9, "stock", 30)
            );

            String filePath = System.getProperty("user.home") + "/Downloads/example.csv";
            
            // 写入CSV
            CsvWriter writer = CsvWriter.create(filePath);
            writer.headers(Map.of("name", "产品名称", "price", "价格", "stock", "库存"))
                  .write(products);
            System.out.println("✓ CSV文件已创建: " + filePath);

            // 读取CSV
            CsvReader reader = CsvReader.open(filePath);
            List<Map<String, Object>> data = reader.read();
            System.out.println("  读取到 " + data.size() + " 行数据");
            data.forEach(row -> System.out.println("  " + row));
            
        } catch (Exception e) {
            System.err.println("CSV操作失败: " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 文件格式转换示例
     */
    private static void fileConversionExample() {
        System.out.println("=== 3. 文件格式转换 ===");
        
        try {
            String excelFile = System.getProperty("user.home") + "/Downloads/example.xlsx";
            String csvFile = System.getProperty("user.home") + "/Downloads/converted.csv";
            
            // Excel转CSV
            try (ExcelReader excelReader = ExcelReader.open(excelFile)) {
                List<Map<String, Object>> data = excelReader.sheet(0).read();
                
                CsvWriter csvWriter = CsvWriter.create(csvFile);
                csvWriter.write(data);
                
                System.out.println("✓ Excel已转换为CSV: " + csvFile);
            }
            
            // CSV转Excel
            String newExcelFile = System.getProperty("user.home") + "/Downloads/converted.xlsx";
            CsvReader csvReader = CsvReader.open(csvFile);
            List<Map<String, Object>> csvData = csvReader.read();
            
            try (ExcelWriter excelWriter = ExcelWriter.create(newExcelFile)) {
                excelWriter.sheet("数据").write(csvData);
                excelWriter.flush();
                System.out.println("✓ CSV已转换为Excel: " + newExcelFile);
            }
            
        } catch (Exception e) {
            System.err.println("格式转换失败: " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * URL读取示例（需要实际的URL才能运行）
     */
    @SuppressWarnings("unused")
    private static void urlReadExample() {
        System.out.println("=== URL读取示例 ===");
        
        try {
            // 从URL读取Excel
            String excelUrl = "https://example.com/data/sample.xlsx";
            try (ExcelReader reader = ExcelReader.openUrl(excelUrl)) {
                Map<String, List<Map<String, Object>>> allData = reader.readAll();
                System.out.println("从URL读取到 " + allData.size() + " 个Sheet");
            }
            
            // 从URL读取CSV
            String csvUrl = "https://example.com/data/sample.csv";
            CsvReader csvReader = CsvReader.openUrl(csvUrl);
            List<Map<String, Object>> csvData = csvReader.read();
            System.out.println("从URL读取到 " + csvData.size() + " 行数据");
            
        } catch (Exception e) {
            System.err.println("URL读取失败: " + e.getMessage());
            System.out.println("提示: 需要提供实际可访问的URL才能运行此示例");
        }
    }
}

