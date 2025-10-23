package io.infra.structure.core.support.document;

import io.infra.structure.core.support.document.csv.CsvReader;
import io.infra.structure.core.support.document.csv.CsvWriter;
import io.infra.structure.core.support.document.excel.ExcelReader;
import io.infra.structure.core.support.document.excel.ExcelWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 无表头文件读取示例
 * 
 * @author infra
 * Created on 2025/10/23
 */
public class NoHeadersExample {

    private static final String TEST_DIR = "target/test-output/";

    public static void main(String[] args) throws IOException {
        excelExample();
        System.out.println();
        csvExample();
    }

    /**
     * Excel无表头示例
     */
    private static void excelExample() throws IOException {
        String filePath = TEST_DIR + "no_headers_example.xlsx";
        
        System.out.println("=== Excel无表头文件读取示例 ===");
        
        // 1. 创建测试文件（包含原始数据，无表头）
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("Sheet1").write(List.of(
                Map.of("col1", "张三", "col2", "25", "col3", "北京"),
                Map.of("col1", "李四", "col2", "30", "col3", "上海"),
                Map.of("col1", "王五", "col2", "28", "col3", "深圳")
            ));
            writer.flush();
        }
        
        // 2. 方式一：读取原始数据（返回List<List<String>>）
        System.out.println("\n方式一：读取原始数据");
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<List<String>> data = reader.sheet(0).readWithoutHeaders();
            for (int i = 0; i < data.size(); i++) {
                System.out.println("第" + (i+1) + "行: " + data.get(i));
            }
        }
        
        // 3. 方式二：提供自定义表头
        System.out.println("\n方式二：使用自定义表头");
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<String> customHeaders = Arrays.asList("姓名", "年龄", "城市");
            List<Map<String, Object>> data = reader.sheet(0).read(customHeaders);
            for (Map<String, Object> row : data) {
                System.out.println(row);
            }
        }
    }

    /**
     * CSV无表头示例
     */
    private static void csvExample() throws IOException {
        String filePath = TEST_DIR + "no_headers_example.csv";
        
        System.out.println("=== CSV无表头文件读取示例 ===");
        
        // 1. 创建测试文件（包含原始数据，无表头）
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(List.of(
            Map.of("col1", "张三", "col2", "25", "col3", "北京"),
            Map.of("col1", "李四", "col2", "30", "col3", "上海"),
            Map.of("col1", "王五", "col2", "28", "col3", "深圳")
        ));
        
        // 2. 方式一：读取原始数据（返回List<List<String>>）
        System.out.println("\n方式一：读取原始数据");
        CsvReader reader1 = CsvReader.open(filePath);
        List<List<String>> data1 = reader1.readWithoutHeaders();
        for (int i = 0; i < data1.size(); i++) {
            System.out.println("第" + (i+1) + "行: " + data1.get(i));
        }
        
        // 3. 方式二：提供自定义表头
        System.out.println("\n方式二：使用自定义表头");
        CsvReader reader2 = CsvReader.open(filePath);
        List<String> customHeaders = Arrays.asList("姓名", "年龄", "城市");
        List<Map<String, Object>> data2 = reader2.read(customHeaders);
        for (Map<String, Object> row : data2) {
            System.out.println(row);
        }
    }
}
