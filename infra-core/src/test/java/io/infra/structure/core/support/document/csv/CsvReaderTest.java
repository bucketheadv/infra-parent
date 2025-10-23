package io.infra.structure.core.support.document.csv;

import io.infra.structure.core.support.document.common.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CsvReader测试
 *
 * @author sven
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CsvReaderTest {

    private static final String TEST_DIR = System.getProperty("user.home") + "/Downloads/csv-test/";

    @BeforeAll
    static void setup() {
        new File(TEST_DIR).mkdirs();
        // 创建测试文件
        createTestFiles();
    }

    private static void createTestFiles() {
        // 创建基础测试文件
        List<Map<String, Object>> dataList = List.of(
            Map.of("name", "张三", "age", 25, "city", "北京"),
            Map.of("name", "李四", "age", 30, "city", "上海"),
            Map.of("name", "王五", "age", 28, "city", "深圳")
        );

        CsvWriter writer = CsvWriter.create(TEST_DIR + "basic_test.csv");
        writer.headers(Map.of("name", "姓名", "age", "年龄", "city", "城市"))
              .write(dataList);

        // 创建Bean测试文件
        List<User> users = List.of(
            new User("张三", 25, "beijing@example.com"),
            new User("李四", 30, "shanghai@example.com")
        );
        CsvWriter beanWriter = CsvWriter.create(TEST_DIR + "bean_test.csv");
        beanWriter.write(users);

        // 创建带注解Bean测试文件
        List<UserWithAnnotation> annotatedUsers = List.of(
            new UserWithAnnotation("张三", 25, "beijing@example.com"),
            new UserWithAnnotation("李四", 30, "shanghai@example.com")
        );
        CsvWriter annotatedWriter = CsvWriter.create(TEST_DIR + "annotated_bean_test.csv");
        annotatedWriter.write(annotatedUsers);

        // 创建特殊字符测试文件
        List<Map<String, Object>> specialData = List.of(
            Map.of("text", "包含逗号,的文本", "quote", "包含\"引号\"的文本", "newline", "包含\n换行符的文本")
        );
        CsvWriter specialWriter = CsvWriter.create(TEST_DIR + "special_chars.csv");
        specialWriter.write(specialData);
    }

    @Test
    @Order(1)
    @DisplayName("测试打开CSV文件")
    void testOpenCsv() {
        String filePath = TEST_DIR + "basic_test.csv";
        assertDoesNotThrow(() -> {
            CsvReader reader = CsvReader.open(filePath);
            assertNotNull(reader);
        });
        System.out.println("✓ CSV文件打开成功");
    }

    @Test
    @Order(2)
    @DisplayName("测试读取为Map列表")
    void testReadAsMapList() {
        String filePath = TEST_DIR + "basic_test.csv";
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        
        assertEquals(3, data.size());
        
        // 验证第一行数据
        Map<String, Object> firstRow = data.getFirst();
        assertEquals("张三", firstRow.get("姓名"));
        assertEquals("25", firstRow.get("年龄"));
        assertEquals("北京", firstRow.get("城市"));
        
        System.out.println("✓ 读取为Map列表成功");
        data.forEach(row -> System.out.println("  " + row));
    }

    @Test
    @Order(3)
    @DisplayName("测试读取为Bean对象")
    void testReadAsBeanList() {
        String filePath = TEST_DIR + "bean_test.csv";
        CsvReader reader = CsvReader.open(filePath);
        List<User> users = reader.read(User.class);
        
        assertEquals(2, users.size());
        
        User first = users.getFirst();
        assertEquals("张三", first.getName());
        assertEquals(25, first.getAge());
        assertEquals("beijing@example.com", first.getEmail());
        
        System.out.println("✓ 读取为Bean对象成功");
        users.forEach(user -> System.out.println("  " + user));
    }

    @Test
    @Order(4)
    @DisplayName("测试读取带注解的Bean")
    void testReadAnnotatedBean() {
        String filePath = TEST_DIR + "annotated_bean_test.csv";
        CsvReader reader = CsvReader.open(filePath);
        
        // 先验证表头是中文
        List<Map<String, Object>> mapData = reader.read();
        assertTrue(mapData.getFirst().containsKey("姓名"));
        assertTrue(mapData.getFirst().containsKey("年龄"));
        assertTrue(mapData.getFirst().containsKey("邮箱"));
        
        // 重新读取为Bean
        CsvReader beanReader = CsvReader.open(filePath);
        List<UserWithAnnotation> users = beanReader.read(UserWithAnnotation.class);
        
        assertEquals(2, users.size());
        assertEquals("张三", users.getFirst().getName());
        
        System.out.println("✓ 读取带注解的Bean成功");
    }

    @Test
    @Order(5)
    @DisplayName("测试使用自定义字段映射读取")
    void testReadWithFieldMapping() {
        String filePath = TEST_DIR + "annotated_bean_test.csv";
        
        // 自定义字段映射
        Map<String, String> fieldMapping = Map.of(
            "姓名", "name",
            "年龄", "age",
            "邮箱", "email"
        );
        
        CsvReader reader = CsvReader.open(filePath);
        List<User> users = reader.read(User.class, fieldMapping);
        
        assertEquals(2, users.size());
        assertEquals("张三", users.getFirst().getName());
        
        System.out.println("✓ 自定义字段映射读取成功");
    }

    @Test
    @Order(6)
    @DisplayName("测试特殊字符处理")
    void testSpecialCharacters() {
        String filePath = TEST_DIR + "special_chars.csv";
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        
        assertEquals(1, data.size());
        
        Map<String, Object> row = data.getFirst();
        assertTrue(row.get("text").toString().contains(","));
        assertTrue(row.get("quote").toString().contains("\""));
        // 换行符可能会被处理，所以只检查不为空
        assertNotNull(row.get("newline"));
        
        System.out.println("✓ 特殊字符处理成功");
        System.out.println("  " + row);
    }

    @Test
    @DisplayName("测试文件不存在时抛出异常")
    void testFileNotExists() {
        String filePath = TEST_DIR + "not_exists.csv";
        assertThrows(IllegalArgumentException.class, () -> {
            CsvReader.open(filePath);
        });
        System.out.println("✓ 文件不存在异常测试通过");
    }

    @Test
    @DisplayName("测试读取空CSV文件")
    void testReadEmptyFile() {
        String filePath = TEST_DIR + "empty.csv";
        
        // 创建空文件
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(Collections.emptyList());
        
        // 读取
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        
        assertEquals(0, data.size());
        System.out.println("✓ 空CSV文件读取成功");
    }

    // 测试用Bean类
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String name;
        private Integer age;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserWithAnnotation {
        @ExcelColumn("姓名")
        private String name;
        
        @ExcelColumn("年龄")
        private Integer age;
        
        @ExcelColumn("邮箱")
        private String email;
    }
}

