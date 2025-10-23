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
 * ExcelWriter测试
 *
 * @author sven
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExcelWriterTest {

    private static final String TEST_DIR = System.getProperty("user.home") + "/Downloads/excel-test/";

    @BeforeAll
    static void setup() {
        new File(TEST_DIR).mkdirs();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建Excel文件")
    void testCreateExcel() {
        String filePath = TEST_DIR + "create_test.xlsx";
        
        assertDoesNotThrow(() -> {
            try (ExcelWriter writer = ExcelWriter.create(filePath)) {
                writer.sheet("测试").write(List.of(Map.of("key", "value")));
                writer.flush();
            }
        });
        
        assertTrue(new File(filePath).exists());
        System.out.println("✓ Excel文件创建成功: " + filePath);
    }

    @Test
    @Order(2)
    @DisplayName("测试写入Map数据")
    void testWriteMapData() {
        List<Map<String, Object>> dataList = List.of(
            Map.of("name", "张三", "age", 25, "salary", 8000.5),
            Map.of("name", "李四", "age", 30, "salary", 12000.0),
            Map.of("name", "王五", "age", 28, "salary", 10000.0)
        );

        String filePath = TEST_DIR + "map_data.xlsx";
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("员工表").write(dataList);
            writer.flush();
        }

        // 验证
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Map<String, Object>> data = reader.sheet(0).read();
            assertEquals(3, data.size());
            System.out.println("✓ Map数据写入成功，行数: " + data.size());
        }
    }

    @Test
    @Order(3)
    @DisplayName("测试写入自定义表头")
    void testWriteWithCustomHeaders() {
        List<Map<String, Object>> dataList = List.of(
            Map.of("name", "张三", "age", 25),
            Map.of("name", "李四", "age", 30)
        );

        Map<String, String> headers = Map.of(
            "name", "姓名",
            "age", "年龄"
        );

        String filePath = TEST_DIR + "custom_headers.xlsx";
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("员工信息")
                  .headers(headers)
                  .write(dataList);
            writer.flush();
        }

        // 验证表头
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Map<String, Object>> data = reader.sheet(0).read();
            assertTrue(data.getFirst().containsKey("姓名"));
            assertTrue(data.getFirst().containsKey("年龄"));
            System.out.println("✓ 自定义表头写入成功");
        }
    }

    @Test
    @Order(4)
    @DisplayName("测试写入Bean对象")
    void testWriteBeanData() {
        List<Employee> employees = List.of(
            new Employee("张三", 25, "技术部", 8000.0),
            new Employee("李四", 30, "销售部", 12000.0)
        );

        String filePath = TEST_DIR + "bean_data.xlsx";
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("员工列表").write(employees);
            writer.flush();
        }

        // 验证
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Employee> readEmployees = reader.sheet(0).read(Employee.class);
            assertEquals(2, readEmployees.size());
            assertEquals("张三", readEmployees.getFirst().getName());
            System.out.println("✓ Bean对象写入成功");
        }
    }

    @Test
    @Order(5)
    @DisplayName("测试写入带注解的Bean")
    void testWriteAnnotatedBean() {
        List<EmployeeWithAnnotation> employees = List.of(
            new EmployeeWithAnnotation("张三", 25, "技术部", 8000.0),
            new EmployeeWithAnnotation("李四", 30, "销售部", 12000.0)
        );

        String filePath = TEST_DIR + "annotated_bean.xlsx";
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("员工信息").write(employees);
            writer.flush();
        }

        // 验证表头是中文
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Map<String, Object>> data = reader.sheet(0).read();
            assertTrue(data.getFirst().containsKey("姓名"));
            assertTrue(data.getFirst().containsKey("年龄"));
            assertTrue(data.getFirst().containsKey("部门"));
            System.out.println("✓ 带注解的Bean写入成功，表头: " + data.getFirst().keySet());
        }
    }

    @Test
    @Order(6)
    @DisplayName("测试多Sheet写入")
    void testWriteMultipleSheets() {
        String filePath = TEST_DIR + "multi_sheets.xlsx";
        
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("员工")
                  .write(List.of(Map.of("name", "张三", "dept", "技术部")));
            
            writer.sheet("产品")
                  .write(List.of(Map.of("product", "产品A", "price", 99.9)));
            
            writer.sheet("订单")
                  .write(List.of(Map.of("order_id", "001", "amount", 1000)));
            
            writer.flush();
        }

        // 验证
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            assertEquals(3, reader.sheetCount());
            assertEquals(List.of("员工", "产品", "订单"), reader.sheetNames());
            System.out.println("✓ 多Sheet写入成功");
        }
    }

    @Test
    @Order(7)
    @DisplayName("测试追加数据")
    void testAppendData() {
        List<Map<String, Object>> initialData = List.of(
            Map.of("name", "张三", "score", 90)
        );

        String filePath = TEST_DIR + "append_test.xlsx";
        
        // 创建初始文件
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("成绩").write(initialData);
            writer.flush();
        }

        // 追加数据
        try (ExcelWriter writer = ExcelWriter.open(filePath)) {
            List<Map<String, Object>> newData = List.of(
                Map.of("name", "李四", "score", 85),
                Map.of("name", "王五", "score", 92)
            );
            writer.sheet("成绩").append(newData);
            writer.flush();
        }

        // 验证
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Map<String, Object>> allData = reader.sheet(0).read();
            assertEquals(3, allData.size());
            System.out.println("✓ 数据追加成功，总行数: " + allData.size());
        }
    }

    @Test
    @Order(8)
    @DisplayName("测试链式调用")
    void testChainedCalls() {
        String filePath = TEST_DIR + "chained.xlsx";
        
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("销售")
                    .headers(Map.of("region", "地区", "amount", "金额"))
                    .write(List.of(Map.of("region", "华东", "amount", 100000)))
                    .done()
                  .sheet("客户")
                    .headers(Map.of("name", "客户名称", "level", "等级"))
                    .write(List.of(Map.of("name", "客户A", "level", "VIP")))
                    .done()
                  .sheet("统计")
                    .write(List.of(Map.of("total", 500000)));
            
            writer.flush();
        }

        // 验证
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            assertEquals(3, reader.sheetCount());
            assertEquals(List.of("销售", "客户", "统计"), reader.sheetNames());
            System.out.println("✓ 链式调用写入成功");
        }
    }

    @Test
    @Order(9)
    @DisplayName("测试清空Sheet")
    void testClearSheet() {
        String filePath = TEST_DIR + "clear_test.xlsx";
        
        // 创建并写入数据
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("数据")
                  .write(List.of(
                      Map.of("id", 1),
                      Map.of("id", 2),
                      Map.of("id", 3)
                  ));
            writer.flush();
        }

        // 清空并写入新数据
        try (ExcelWriter writer = ExcelWriter.open(filePath)) {
            writer.sheet("数据")
                  .clear()
                  .write(List.of(Map.of("id", 999)));
            writer.flush();
        }

        // 验证
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Map<String, Object>> data = reader.sheet(0).read();
            assertEquals(1, data.size());
            assertEquals("999", data.getFirst().get("id"));
            System.out.println("✓ Sheet清空成功");
        }
    }

    @Test
    @Order(10)
    @DisplayName("测试打开已存在文件进行编辑")
    void testOpenExistingFile() {
        String filePath = TEST_DIR + "existing_test.xlsx";
        
        // 创建初始文件
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("Sheet1").write(List.of(Map.of("data", "initial")));
            writer.flush();
        }

        // 打开并添加新Sheet
        try (ExcelWriter writer = ExcelWriter.open(filePath)) {
            writer.sheet("Sheet2").write(List.of(Map.of("data", "new")));
            writer.flush();
        }

        // 验证
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            assertEquals(2, reader.sheetCount());
            assertTrue(reader.sheetNames().contains("Sheet1"));
            assertTrue(reader.sheetNames().contains("Sheet2"));
            System.out.println("✓ 打开已存在文件编辑成功");
        }
    }

    @Test
    @DisplayName("测试空数据写入")
    void testWriteEmptyData() {
        String filePath = TEST_DIR + "empty_data.xlsx";
        
        try (ExcelWriter writer = ExcelWriter.create(filePath)) {
            writer.sheet("空Sheet").write(Collections.emptyList());
            writer.flush();
        }

        // 验证文件创建成功但无数据
        try (ExcelReader reader = ExcelReader.open(filePath)) {
            List<Map<String, Object>> data = reader.sheet(0).read();
            assertEquals(0, data.size());
            System.out.println("✓ 空数据写入成功");
        }
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

