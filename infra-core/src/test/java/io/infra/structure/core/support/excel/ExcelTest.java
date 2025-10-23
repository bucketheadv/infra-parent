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
 * Excel测试类
 *
 * @author sven
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExcelTest {

    @Test
    public void testCreateAndWrite() {
        List<Map<String, Object>> dataList = new ArrayList<>();

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "张三");
        row1.put("age", 25);
        row1.put("salary", 8000.5);
        dataList.add(row1);

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("name", "李四");
        row2.put("age", 30);
        row2.put("salary", 12000.0);
        dataList.add(row2);

        String filePath = System.getProperty("user.home") + "/Downloads/test_excel.xlsx";
        try (Excel excel = Excel.create(filePath)) {
            excel.sheet("员工表").write(dataList);
            excel.flush();
        }

        System.out.println("Excel文件已创建: " + filePath);
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

        String filePath = System.getProperty("user.home") + "/Downloads/test_custom_headers.xlsx";
        try (Excel excel = Excel.create(filePath)) {
            excel.sheet("员工信息")
                    .headers(headers)
                    .write(dataList);
            excel.flush();
        }

        System.out.println("自定义表头的Excel文件已创建: " + filePath);
    }

    @Test
    @Order(1)
    public void testMultipleSheets() {
        List<Map<String, Object>> employeeData = new ArrayList<>();
        Map<String, Object> emp1 = new LinkedHashMap<>();
        emp1.put("name", "张三");
        emp1.put("age", 25);
        employeeData.add(emp1);

        List<Map<String, Object>> productData = new ArrayList<>();
        Map<String, Object> prod1 = new LinkedHashMap<>();
        prod1.put("product_name", "产品A");
        prod1.put("price", 99.9);
        productData.add(prod1);

        String filePath = System.getProperty("user.home") + "/Downloads/test_multi_sheet.xlsx";
        try (Excel excel = Excel.create(filePath)) {
            excel.sheet("员工表").write(employeeData);
            excel.sheet("产品表").write(productData);
            excel.flush();
        }

        System.out.println("多Sheet的Excel文件已创建: " + filePath);
    }

    @Test
    public void testChainedCalls() {
        List<Map<String, Object>> data1 = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("region", "华东");
        row1.put("amount", 1000000.0);
        data1.add(row1);

        List<Map<String, Object>> data2 = new ArrayList<>();
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", "C001");
        row2.put("name", "客户A");
        data2.add(row2);

        String filePath = System.getProperty("user.home") + "/Downloads/test_chained.xlsx";
        try (Excel excel = Excel.create(filePath)) {
            excel.sheet("销售汇总")
                    .headers(Map.of("region", "地区", "amount", "销售额"))
                    .write(data1)
                    .done()
                    .sheet("客户明细")
                    .headers(Map.of("id", "客户编号", "name", "客户名称"))
                    .write(data2);

            excel.flush();
        }

        System.out.println("链式调用创建的Excel文件: " + filePath);
    }

    @Test
    @Order(2)
    public void testRead() {
        String filePath = System.getProperty("user.home") + "/Downloads/test_multi_sheet.xlsx";
        if (!new File(filePath).exists()) {
            System.out.println("文件不存在，请先运行testMultipleSheets创建文件");
            return;
        }

        try (Excel excel = Excel.open(filePath)) {
            System.out.println("文件中有 " + excel.sheetCount() + " 个sheet");
            System.out.println("Sheet名称: " + excel.sheetNames());

            List<Map<String, Object>> data = excel.sheetAt(0).read();
            System.out.println("\n第一个Sheet的数据:");
            data.forEach(System.out::println);
        }
    }

    @Test
    public void testBeans() {
        List<Employee> employees = new ArrayList<>();
        employees.add(new Employee("张三", 25, 8000.5, "开发部"));
        employees.add(new Employee("李四", 30, 12000.0, "产品部"));

        String filePath = System.getProperty("user.home") + "/Downloads/test_beans.xlsx";
        try (Excel excel = Excel.create(filePath)) {
            excel.sheet("员工列表").write(employees);
            excel.flush();
        }

        try (Excel excel = Excel.open(filePath)) {
            List<Employee> readEmployees = excel.sheetAt(0).read(Employee.class);
            System.out.println("读取到 " + readEmployees.size() + " 个员工:");
            readEmployees.forEach(System.out::println);
        }
    }

    @Test
    public void testBeansWithAnnotation() {
        // 使用@Column注解的Bean
        List<EmployeeWithAnnotation> employees = new ArrayList<>();
        employees.add(new EmployeeWithAnnotation("张三", 25, 8000.5, "开发部"));
        employees.add(new EmployeeWithAnnotation("李四", 30, 12000.0, "产品部"));
        employees.add(new EmployeeWithAnnotation("王五", 28, 10000.0, "测试部"));

        String filePath = System.getProperty("user.home") + "/Downloads/test_beans_annotation.xlsx";
        try (Excel excel = Excel.create(filePath)) {
            // 写入时自动使用@ExcelColumn注解的值作为表头
            excel.sheet("员工列表").write(employees);
            excel.flush();
        }

        try (Excel excel = Excel.open(filePath)) {
            // 读取时自动根据@ExcelColumn注解匹配字段
            List<EmployeeWithAnnotation> readEmployees = excel.sheetAt(0).read(EmployeeWithAnnotation.class);
            System.out.println("读取到 " + readEmployees.size() + " 个员工:");
            readEmployees.forEach(System.out::println);
        }
    }

    @Test
    public void testBeansWithFieldMapping() {
        // 测试使用自定义字段映射读取Excel
        List<Employee> employees = new ArrayList<>();
        employees.add(new Employee("张三", 25, 8000.5, "开发部"));
        employees.add(new Employee("李四", 30, 12000.0, "产品部"));

        String filePath = System.getProperty("user.home") + "/Downloads/test_field_mapping.xlsx";
        
        // 写入时使用中文表头
        Map<String, String> writeHeaders = new LinkedHashMap<>();
        writeHeaders.put("name", "姓名");
        writeHeaders.put("age", "年龄");
        writeHeaders.put("salary", "薪资");
        writeHeaders.put("department", "部门");
        
        try (Excel excel = Excel.create(filePath)) {
            excel.sheet("员工表")
                .headers(writeHeaders)
                .write(employees.stream()
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
            excel.flush();
        }

        // 读取时使用字段映射将中文表头映射回字段名
        Map<String, String> fieldMapping = new LinkedHashMap<>();
        fieldMapping.put("姓名", "name");
        fieldMapping.put("年龄", "age");
        fieldMapping.put("薪资", "salary");
        fieldMapping.put("部门", "department");
        
        try (Excel excel = Excel.open(filePath)) {
            List<Employee> readEmployees = excel.sheetAt(0).read(Employee.class, fieldMapping);
            System.out.println("使用字段映射读取到 " + readEmployees.size() + " 个员工:");
            readEmployees.forEach(System.out::println);
        }
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

