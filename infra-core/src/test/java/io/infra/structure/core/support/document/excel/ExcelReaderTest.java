package io.infra.structure.core.support.document.excel;

import io.infra.structure.core.support.document.common.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExcelReader测试
 *
 * @author sven
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExcelReaderTest {

    private static final String TEST_DIR = System.getProperty("user.home") + "/Downloads/excel-test/";

    @BeforeAll
    static void setup() {
        new File(TEST_DIR).mkdirs();
        // 创建测试文件
        createTestFiles();
    }

    private static void createTestFiles() {
        // 创建基础测试文件
        List<Map<String, Object>> dataList = List.of(
            Map.of("name", "张三", "age", 25, "salary", 8000.5),
            Map.of("name", "李四", "age", 30, "salary", 12000.0),
            Map.of("name", "王五", "age", 28, "salary", 10000.0)
        );

        try (ExcelWriter writer = ExcelWriter.create(TEST_DIR + "basic_test.xlsx")) {
            writer.sheet("员工表")
                  .headers(Map.of("name", "姓名", "age", "年龄", "salary", "薪资"))
                  .write(dataList);
            writer.flush();
        }

        // 创建多Sheet测试文件
        try (ExcelWriter writer = ExcelWriter.create(TEST_DIR + "multi_sheets.xlsx")) {
            writer.sheet("员工").write(List.of(Map.of("name", "张三", "dept", "技术部")));
            writer.sheet("产品").write(List.of(Map.of("product", "产品A", "price", 99.9)));
            writer.sheet("订单").write(List.of(Map.of("order_id", "001", "amount", 1000)));
            writer.flush();
        }

        // 创建Bean测试文件
        List<Employee> employees = List.of(
            new Employee("张三", 25, "技术部", 8000.0),
            new Employee("李四", 30, "销售部", 12000.0)
        );
        try (ExcelWriter writer = ExcelWriter.create(TEST_DIR + "bean_test.xlsx")) {
            writer.sheet("员工列表").write(employees);
            writer.flush();
        }

        // 创建带注解Bean测试文件
        List<EmployeeWithAnnotation> annotatedEmployees = List.of(
            new EmployeeWithAnnotation("张三", 25, "技术部", 8000.0),
            new EmployeeWithAnnotation("李四", 30, "销售部", 12000.0)
        );
        try (ExcelWriter writer = ExcelWriter.create(TEST_DIR + "annotated_bean_test.xlsx")) {
            writer.sheet("员工信息").write(annotatedEmployees);
            writer.flush();
        }
    }

    @Test
    @Order(1)
    @DisplayName("测试打开Excel文件")
    void testOpenExcel() {
        String filePath = TEST_DIR + "basic_test.xlsx";
        assertDoesNotThrow(() -> {
            try (ExcelReader reader = ExcelReader.open(filePath)) {
                assertNotNull(reader);
            }
        });
        System.out.println("✓ Excel文件打开成功");
    }

    @Test
    @Order(2)
    @DisplayName("测试读取Sheet数量和名称")
    void testSheetInfo() {
        String filePath = TEST_DIR + "multi_sheets.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            assertEquals(3, reader.sheetCount());
            
            List<String> sheetNames = reader.sheetNames();
            assertEquals(3, sheetNames.size());
            assertTrue(sheetNames.contains("员工"));
            assertTrue(sheetNames.contains("产品"));
            assertTrue(sheetNames.contains("订单"));
            
            System.out.println("✓ Sheet信息读取成功: " + sheetNames);
        }
    }

    @Test
    @Order(3)
    @DisplayName("测试根据索引读取Sheet")
    void testReadSheetByIndex() {
        String filePath = TEST_DIR + "basic_test.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            ExcelReader.SheetReader sheet = reader.sheet(0);
            assertNotNull(sheet);
            assertEquals("员工表", sheet.name());
            
            List<Map<String, Object>> data = sheet.read();
            assertEquals(3, data.size());
            
            System.out.println("✓ 根据索引读取Sheet成功，数据行数: " + data.size());
        }
    }

    @Test
    @Order(4)
    @DisplayName("测试根据名称读取Sheet")
    void testReadSheetByName() {
        String filePath = TEST_DIR + "multi_sheets.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            ExcelReader.SheetReader sheet = reader.sheet("产品");
            assertNotNull(sheet);
            assertEquals("产品", sheet.name());
            
            List<Map<String, Object>> data = sheet.read();
            assertEquals(1, data.size());
            
            Map<String, Object> firstRow = data.getFirst();
            assertEquals("产品A", firstRow.get("product"));
            
            System.out.println("✓ 根据名称读取Sheet成功: " + firstRow);
        }
    }

    @Test
    @Order(5)
    @DisplayName("测试读取为Map列表")
    void testReadAsMapList() {
        String filePath = TEST_DIR + "basic_test.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Map<String, Object>> data = reader.sheet(0).read();
            
            assertEquals(3, data.size());
            
            // 验证第一行数据
            Map<String, Object> firstRow = data.getFirst();
            assertEquals("张三", firstRow.get("姓名"));
            assertEquals("25", firstRow.get("年龄"));
            assertTrue(firstRow.get("薪资").toString().startsWith("8000"));
            
            System.out.println("✓ 读取为Map列表成功");
            data.forEach(row -> System.out.println("  " + row));
        }
    }

    @Test
    @Order(6)
    @DisplayName("测试读取为Bean对象")
    void testReadAsBeanList() {
        String filePath = TEST_DIR + "bean_test.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Employee> employees = reader.sheet(0).read(Employee.class);
            
            assertEquals(2, employees.size());
            
            Employee first = employees.getFirst();
            assertEquals("张三", first.getName());
            assertEquals(25, first.getAge());
            assertEquals("技术部", first.getDepartment());
            
            System.out.println("✓ 读取为Bean对象成功");
            employees.forEach(emp -> System.out.println("  " + emp));
        }
    }

    @Test
    @Order(7)
    @DisplayName("测试读取带注解的Bean")
    void testReadAnnotatedBean() {
        String filePath = TEST_DIR + "annotated_bean_test.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            // 先验证表头是中文
            List<Map<String, Object>> mapData = reader.sheet(0).read();
            assertTrue(mapData.getFirst().containsKey("姓名"));
            assertTrue(mapData.getFirst().containsKey("年龄"));
            assertTrue(mapData.getFirst().containsKey("部门"));
        }
        
        // 重新打开读取为Bean
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<EmployeeWithAnnotation> employees = reader.sheet(0).read(EmployeeWithAnnotation.class);
            
            assertEquals(2, employees.size());
            assertEquals("张三", employees.getFirst().getName());
            
            System.out.println("✓ 读取带注解的Bean成功");
        }
    }

    @Test
    @Order(8)
    @DisplayName("测试使用自定义字段映射读取")
    void testReadWithFieldMapping() {
        String filePath = TEST_DIR + "annotated_bean_test.xlsx";
        
        // 自定义字段映射
        Map<String, String> fieldMapping = Map.of(
            "姓名", "name",
            "年龄", "age",
            "部门", "department",
            "薪资", "salary"
        );
        
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Employee> employees = reader.sheet(0).read(Employee.class, fieldMapping);
            
            assertEquals(2, employees.size());
            assertEquals("张三", employees.getFirst().getName());
            
            System.out.println("✓ 自定义字段映射读取成功");
        }
    }

    @Test
    @Order(9)
    @DisplayName("测试读取所有Sheet")
    void testReadAllSheets() {
        String filePath = TEST_DIR + "multi_sheets.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            Map<String, List<Map<String, Object>>> allData = reader.readAll();
            
            assertEquals(3, allData.size());
            assertTrue(allData.containsKey("员工"));
            assertTrue(allData.containsKey("产品"));
            assertTrue(allData.containsKey("订单"));
            
            System.out.println("✓ 读取所有Sheet成功");
            allData.forEach((sheetName, data) -> 
                System.out.println("  " + sheetName + ": " + data.size() + " 行"));
        }
    }

    @Test
    @Order(10)
    @DisplayName("测试获取Sheet行数")
    void testGetRowCount() {
        String filePath = TEST_DIR + "basic_test.xlsx";
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            ExcelReader.SheetReader sheet = reader.sheet(0);
            int rowCount = sheet.rowCount();
            
            // rowCount不包括表头，所以应该是3
            assertTrue(rowCount >= 3);
            
            System.out.println("✓ 获取行数成功: " + rowCount);
        }
    }

    @Test
    @DisplayName("测试文件不存在时抛出异常")
    void testFileNotExists() {
        String filePath = TEST_DIR + "not_exists.xlsx";
        assertThrows(IllegalArgumentException.class, () -> {
            ExcelReader.open(filePath);
        });
        System.out.println("✓ 文件不存在异常测试通过");
    }

    // 测试用Bean类
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Employee {
        private String name;
        private Integer age;
        private String department;
        private Double salary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeWithAnnotation {
        @ExcelColumn("姓名")
        private String name;
        
        @ExcelColumn("年龄")
        private Integer age;
        
        @ExcelColumn("部门")
        private String department;
        
        @ExcelColumn("薪资")
        private Double salary;
    }
}

