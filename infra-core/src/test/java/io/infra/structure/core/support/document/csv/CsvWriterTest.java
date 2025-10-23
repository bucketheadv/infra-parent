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
 * CsvWriter测试
 *
 * @author sven
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CsvWriterTest {

    private static final String TEST_DIR = System.getProperty("user.home") + "/Downloads/csv-test/";

    @BeforeAll
    static void setup() {
        new File(TEST_DIR).mkdirs();
    }

    @Test
    @Order(1)
    @DisplayName("测试创建CSV文件")
    void testCreateCsv() {
        String filePath = TEST_DIR + "create_test.csv";
        
        assertDoesNotThrow(() -> {
            CsvWriter writer = CsvWriter.create(filePath);
            writer.write(List.of(Map.of("key", "value")));
        });
        
        assertTrue(new File(filePath).exists());
        System.out.println("✓ CSV文件创建成功: " + filePath);
    }

    @Test
    @Order(2)
    @DisplayName("测试写入Map数据")
    void testWriteMapData() {
        List<Map<String, Object>> dataList = List.of(
            Map.of("name", "张三", "age", 25, "city", "北京"),
            Map.of("name", "李四", "age", 30, "city", "上海"),
            Map.of("name", "王五", "age", 28, "city", "深圳")
        );

        String filePath = TEST_DIR + "map_data.csv";
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(dataList);

        // 验证
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertEquals(3, data.size());
        System.out.println("✓ Map数据写入成功，行数: " + data.size());
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

        String filePath = TEST_DIR + "custom_headers.csv";
        CsvWriter writer = CsvWriter.create(filePath);
        writer.headers(headers).write(dataList);

        // 验证表头
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertTrue(data.getFirst().containsKey("姓名"));
        assertTrue(data.getFirst().containsKey("年龄"));
        System.out.println("✓ 自定义表头写入成功");
    }

    @Test
    @Order(4)
    @DisplayName("测试写入Bean对象")
    void testWriteBeanData() {
        List<User> users = List.of(
            new User("张三", 25, "beijing@example.com"),
            new User("李四", 30, "shanghai@example.com")
        );

        String filePath = TEST_DIR + "bean_data.csv";
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(users);

        // 验证
        CsvReader reader = CsvReader.open(filePath);
        List<User> readUsers = reader.read(User.class);
        assertEquals(2, readUsers.size());
        assertEquals("张三", readUsers.getFirst().getName());
        System.out.println("✓ Bean对象写入成功");
    }

    @Test
    @Order(5)
    @DisplayName("测试写入带注解的Bean")
    void testWriteAnnotatedBean() {
        List<UserWithAnnotation> users = List.of(
            new UserWithAnnotation("张三", 25, "beijing@example.com"),
            new UserWithAnnotation("李四", 30, "shanghai@example.com")
        );

        String filePath = TEST_DIR + "annotated_bean.csv";
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(users);

        // 验证表头是中文
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertTrue(data.getFirst().containsKey("姓名"));
        assertTrue(data.getFirst().containsKey("年龄"));
        assertTrue(data.getFirst().containsKey("邮箱"));
        System.out.println("✓ 带注解的Bean写入成功，表头: " + data.getFirst().keySet());
    }

    @Test
    @Order(6)
    @DisplayName("测试追加数据")
    void testAppendData() {
        List<Map<String, Object>> initialData = List.of(
            Map.of("product", "产品A", "price", 99.9)
        );

        String filePath = TEST_DIR + "append_test.csv";
        
        // 创建初始文件
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(initialData);

        // 追加数据
        CsvWriter appendWriter = CsvWriter.open(filePath);
        List<Map<String, Object>> newData = List.of(
            Map.of("product", "产品B", "price", 199.9),
            Map.of("product", "产品C", "price", 299.9)
        );
        appendWriter.append(newData);

        // 验证
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> allData = reader.read();
        assertEquals(3, allData.size());
        System.out.println("✓ 数据追加成功，总行数: " + allData.size());
    }

    @Test
    @Order(7)
    @DisplayName("测试特殊字符处理")
    void testSpecialCharacters() {
        List<Map<String, Object>> dataList = List.of(
            Map.of("text", "包含逗号,的文本", "quote", "包含\"引号\"的文本", "newline", "包含\n换行符的文本")
        );

        String filePath = TEST_DIR + "special_chars.csv";
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(dataList);

        // 读取并验证
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> readData = reader.read();
        
        assertEquals(1, readData.size());
        assertTrue(readData.getFirst().get("text").toString().contains(","));
        assertTrue(readData.getFirst().get("quote").toString().contains("\""));
        
        System.out.println("✓ 特殊字符处理成功");
    }

    @Test
    @Order(8)
    @DisplayName("测试链式调用")
    void testChainedCalls() {
        String filePath = TEST_DIR + "chained.csv";
        
        CsvWriter writer = CsvWriter.create(filePath);
        writer.headers(Map.of("name", "姓名", "age", "年龄", "city", "城市"))
              .write(List.of(
                  Map.of("name", "张三", "age", 25, "city", "北京"),
                  Map.of("name", "李四", "age", 30, "city", "上海")
              ));

        // 验证
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertEquals(2, data.size());
        System.out.println("✓ 链式调用写入成功");
    }

    @Test
    @Order(9)
    @DisplayName("测试打开已存在文件追加")
    void testOpenExistingFile() {
        String filePath = TEST_DIR + "existing_test.csv";
        
        // 创建初始文件
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(List.of(Map.of("data", "initial")));

        // 打开并追加
        CsvWriter appendWriter = CsvWriter.open(filePath);
        appendWriter.append(List.of(Map.of("data", "appended")));

        // 验证
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertEquals(2, data.size());
        System.out.println("✓ 打开已存在文件追加成功");
    }

    @Test
    @DisplayName("测试空数据写入")
    void testWriteEmptyData() {
        String filePath = TEST_DIR + "empty_data.csv";
        
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(Collections.emptyList());

        // 验证文件创建成功但无数据
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertEquals(0, data.size());
        System.out.println("✓ 空数据写入成功");
    }

    @Test
    @DisplayName("测试大量数据写入")
    void testWriteLargeData() {
        List<Map<String, Object>> largeData = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            largeData.add(Map.of(
                "id", i,
                "name", "用户" + i,
                "score", 50 + (i % 50)
            ));
        }

        String filePath = TEST_DIR + "large_data.csv";
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(largeData);

        // 验证
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertEquals(1000, data.size());
        System.out.println("✓ 大量数据写入成功，行数: " + data.size());
    }

    @Test
    @DisplayName("测试多次追加")
    void testMultipleAppends() {
        String filePath = TEST_DIR + "multiple_appends.csv";
        
        // 创建初始文件
        CsvWriter writer = CsvWriter.create(filePath);
        writer.write(List.of(Map.of("seq", 1)));

        // 多次追加
        for (int i = 2; i <= 5; i++) {
            CsvWriter appendWriter = CsvWriter.open(filePath);
            appendWriter.append(List.of(Map.of("seq", i)));
        }

        // 验证
        CsvReader reader = CsvReader.open(filePath);
        List<Map<String, Object>> data = reader.read();
        assertEquals(5, data.size());
        System.out.println("✓ 多次追加成功，总行数: " + data.size());
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

