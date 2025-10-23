package io.infra.structure.core.support.excel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.util.*;

/**
 * CSV测试类
 *
 * @author sven
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CsvTest {

    @Test
    @Order(1)
    public void testCreateAndWrite() {
        List<Map<String, Object>> dataList = new ArrayList<>();

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "张三");
        row1.put("age", 25);
        row1.put("remark", "优秀员工,表现突出");
        dataList.add(row1);

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("name", "李四");
        row2.put("age", 30);
        row2.put("remark", "部门经理");
        dataList.add(row2);

        String filePath = System.getProperty("user.home") + "/Downloads/test_csv.csv";
        Csv csv = Csv.create(filePath);
        csv.write(dataList);
    }

    @Test
    public void testWithCustomHeaders() {
        List<Map<String, Object>> dataList = new ArrayList<>();

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "张三");
        row1.put("age", 25);
        row1.put("salary", 8000.5);
        dataList.add(row1);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("name", "姓名");
        headers.put("age", "年龄");
        headers.put("salary", "薪资");

        String filePath = System.getProperty("user.home") + "/Downloads/test_csv_headers.csv";
        Csv csv = Csv.create(filePath);
        csv.headers(headers).write(dataList);
    }

    @Test
    @Order(2)
    public void testRead() {
        String filePath = System.getProperty("user.home") + "/Downloads/test_csv.csv";
        if (!new File(filePath).exists()) {
            System.out.println("文件不存在，请先运行testCreateAndWrite创建文件");
            return;
        }

        Csv csv = Csv.open(filePath);
        List<Map<String, Object>> dataList = csv.read();
        System.out.println("读取到 " + dataList.size() + " 行数据:");
        dataList.forEach(System.out::println);
    }

    @Test
    public void testAppend() {
        String filePath = System.getProperty("user.home") + "/Downloads/test_append.csv";

        // 初始写入
        List<Map<String, Object>> initialData = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "张三");
        row1.put("age", 25);
        initialData.add(row1);

        Csv csv = Csv.create(filePath);
        csv.write(initialData);

        // 追加数据
        List<Map<String, Object>> appendData = new ArrayList<>();
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("name", "李四");
        row2.put("age", 30);
        appendData.add(row2);

        csv = Csv.open(filePath);
        csv.append(appendData);
    }

    @Test
    public void testBeans() {
        List<Employee> employees = new ArrayList<>();
        employees.add(new Employee("张三", 25, 8000.5, "开发部"));
        employees.add(new Employee("李四", 30, 12000.0, "产品部"));

        String filePath = System.getProperty("user.home") + "/Downloads/test_beans.csv";
        Csv csv = Csv.create(filePath);
        // 写入Bean时不使用自定义表头，保持字段名以便读取时匹配
        csv.write(employees);

        csv = Csv.open(filePath);
        List<Employee> readEmployees = csv.read(Employee.class);
        System.out.println("读取到 " + readEmployees.size() + " 个员工:");
        readEmployees.forEach(System.out::println);
    }

    @Test
    public void testBeansWithAnnotation() {
        // 使用@Column注解的Bean
        List<EmployeeWithAnnotation> employees = new ArrayList<>();
        employees.add(new EmployeeWithAnnotation("张三", 25, 8000.5, "开发部"));
        employees.add(new EmployeeWithAnnotation("李四", 30, 12000.0, "产品部"));

        String filePath = System.getProperty("user.home") + "/Downloads/test_beans_annotation.csv";
        Csv csv = Csv.create(filePath);
        // 写入时自动使用@ExcelColumn注解的值作为表头
        csv.write(employees);

        csv = Csv.open(filePath);
        // 读取时自动根据@ExcelColumn注解匹配字段
        List<EmployeeWithAnnotation> readEmployees = csv.read(EmployeeWithAnnotation.class);
        System.out.println("读取到 " + readEmployees.size() + " 个员工:");
        readEmployees.forEach(System.out::println);
    }

    @Test
    public void testBeansWithFieldMapping() {
        // 测试使用自定义字段映射读取CSV
        List<Employee> employees = new ArrayList<>();
        employees.add(new Employee("张三", 25, 8000.5, "开发部"));
        employees.add(new Employee("李四", 30, 12000.0, "产品部"));

        String filePath = System.getProperty("user.home") + "/Downloads/test_field_mapping.csv";
        
        // 写入时使用中文表头
        Map<String, String> writeHeaders = new LinkedHashMap<>();
        writeHeaders.put("name", "姓名");
        writeHeaders.put("age", "年龄");
        writeHeaders.put("salary", "薪资");
        writeHeaders.put("department", "部门");
        
        Csv csv = Csv.create(filePath);
        csv.headers(writeHeaders).write(
            employees.stream()
                .map(e -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("name", e.getName());
                    map.put("age", e.getAge());
                    map.put("salary", e.getSalary());
                    map.put("department", e.getDepartment());
                    return map;
                })
                .toList()
        );

        // 读取时使用字段映射将中文表头映射回字段名
        Map<String, String> fieldMapping = new LinkedHashMap<>();
        fieldMapping.put("姓名", "name");
        fieldMapping.put("年龄", "age");
        fieldMapping.put("薪资", "salary");
        fieldMapping.put("部门", "department");
        
        csv = Csv.open(filePath);
        List<Employee> readEmployees = csv.read(Employee.class, fieldMapping);
        System.out.println("使用字段映射读取到 " + readEmployees.size() + " 个员工:");
        readEmployees.forEach(System.out::println);
    }

    @Test
    public void testSpecialCharacters() {
        List<Map<String, Object>> dataList = new ArrayList<>();

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "张三");
        row1.put("description", "这是包含逗号,的描述");
        row1.put("remark", "这是包含\"引号\"的内容");
        dataList.add(row1);

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("name", "李四");
        row2.put("description", "正常描述");
        row2.put("remark", "多行内容\n第二行");
        dataList.add(row2);

        String filePath = System.getProperty("user.home") + "/Downloads/test_special.csv";
        Csv csv = Csv.create(filePath);
        csv.write(dataList);

        csv = Csv.open(filePath);
        List<Map<String, Object>> result = csv.read();
        System.out.println("读取到 " + result.size() + " 行数据:");
        result.forEach(System.out::println);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Employee {
        private String name;
        private Integer age;
        private Double salary;
        private String department;
    }

    /**
     * 带@ExcelColumn注解的员工类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EmployeeWithAnnotation {
        @ExcelColumn("姓名")
        private String name;
        
        @ExcelColumn("年龄")
        private Integer age;
        
        @ExcelColumn("薪资")
        private Double salary;
        
        @ExcelColumn("部门")
        private String department;
    }
}

