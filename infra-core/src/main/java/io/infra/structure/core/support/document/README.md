# Excel和CSV工具类使用文档

## 概述

本模块提供了Excel和CSV文件的读写工具类，采用Reader/Writer分离设计，遵循单一职责原则。

### 主要特性

✅ 支持本地文件和URL读取  
✅ 读写功能分离（Reader/Writer）  
✅ 支持Map和Bean对象互转  
✅ 支持自定义表头映射  
✅ 支持@ExcelColumn注解  
✅ 链式API调用  

## 包结构

```
io.infra.structure.core.support.document/
├── excel/              - Excel操作相关
│   ├── ExcelReader     - Excel读取工具
│   ├── ExcelWriter     - Excel写入工具
│   └── CellHelper      - Excel单元格操作工具
├── csv/                - CSV操作相关
│   ├── CsvReader       - CSV读取工具
│   ├── CsvWriter       - CSV写入工具
│   └── CsvHelper       - CSV格式处理工具
└── common/             - 公共组件
    ├── BeanConverter   - Bean与Map转换工具
    └── ExcelColumn     - 字段注解
```

### 设计原则

- **单一职责**：读写功能完全分离，每个类职责明确
- **代码复用**：提取公共工具类（BeanConverter、CsvHelper、CellHelper）
- **清晰架构**：按功能模块划分包，便于维护和扩展
- **低耦合**：Excel和CSV模块相互独立，仅依赖common包

## 快速开始

### 1. ExcelReader - 读取Excel文件

```java

```

#### 从本地文件读取

```java
// 读取为Map列表
try (ExcelReader reader = ExcelReader.open("data.xlsx")) {
    List<Map<String, Object>> data = reader.sheet(0).read();
    data.forEach(System.out::println);
}

// 读取为Bean对象
try (ExcelReader reader = ExcelReader.open("users.xlsx")) {
    List<User> users = reader.sheet("用户表").read(User.class);
    users.forEach(System.out::println);
}
```

#### 从URL读取

```java
String url = "https://example.com/data/sample.xlsx";
try (ExcelReader reader = ExcelReader.openUrl(url)) {
    // 读取所有Sheet
    Map<String, List<Map<String, Object>>> allData = reader.readAll();
    
    // 读取指定Sheet
    ExcelReader.SheetReader sheet = reader.sheet("员工表");
    List<Map<String, Object>> data = sheet.read();
}
```

#### 从InputStream读取

```java
try (InputStream is = ...; 
     ExcelReader reader = ExcelReader.open(is, "data.xlsx")) {
    List<Map<String, Object>> data = reader.sheet(0).read();
}
```

### 2. ExcelWriter - 写入Excel文件

```java

```

#### 创建新文件

```java
List<Map<String, Object>> dataList = List.of(
    Map.of("name", "张三", "age", 25, "dept", "技术部"),
    Map.of("name", "李四", "age", 30, "dept", "销售部")
);

try (ExcelWriter writer = ExcelWriter.create("output.xlsx")) {
    writer.sheet("员工表")
          .headers(Map.of("name", "姓名", "age", "年龄", "dept", "部门"))
          .write(dataList);
    writer.flush();
}
```

#### 编辑现有文件

```java
try (ExcelWriter writer = ExcelWriter.open("existing.xlsx")) {
    // 追加数据到现有Sheet
    writer.sheet("员工表").append(newDataList);
    
    // 创建新Sheet
    writer.sheet("新表").write(dataList);
    
    writer.flush();
}
```

#### 多Sheet写入

```java
try (ExcelWriter writer = ExcelWriter.create("report.xlsx")) {
    // 写入第一个Sheet
    writer.sheet("销售数据").write(salesData);
    
    // 写入第二个Sheet
    writer.sheet("统计汇总").write(summaryData);
    
    writer.flush();
}
```

### 3. CsvReader - 读取CSV文件

```java

```

#### 从本地文件读取

```java
// 读取为Map列表
CsvReader reader = CsvReader.open("data.csv");
List<Map<String, Object>> data = reader.read();

// 读取为Bean对象
List<User> users = reader.read(User.class);

// 自定义字段映射
Map<String, String> fieldMapping = Map.of(
    "姓名", "name",
    "年龄", "age"
);
List<User> users = reader.read(User.class, fieldMapping);
```

#### 从URL读取

```java
String url = "https://example.com/data/sample.csv";
CsvReader reader = CsvReader.openUrl(url);
List<Map<String, Object>> data = reader.read();
```

#### 从InputStream读取

```java
InputStream is = ...;
CsvReader reader = CsvReader.open(is);
List<Map<String, Object>> data = reader.read();
```

### 4. CsvWriter - 写入CSV文件

```java

```

#### 创建新文件

```java
List<Map<String, Object>> dataList = List.of(
    Map.of("name", "张三", "age", 25, "city", "北京"),
    Map.of("name", "李四", "age", 30, "city", "上海")
);

CsvWriter writer = CsvWriter.create("output.csv");
writer.headers(Map.of("name", "姓名", "age", "年龄", "city", "城市"))
      .write(dataList);
```

#### 追加数据

```java
CsvWriter writer = CsvWriter.open("existing.csv");
writer.append(newDataList);
```

## Bean转换

### 使用@ExcelColumn注解

```java
import io.infra.structure.core.support.document.common.ExcelColumn;

public class User {
    @ExcelColumn("姓名")
    private String name;

    @ExcelColumn("年龄")
    private Integer age;

    @ExcelColumn("部门")
    private String department;
}

// 读取时自动映射
List<User> users = reader.read(User.class);

// 写入时自动使用注解值作为表头
writer.

write(userList);
```

### 不使用注解

```java
// 使用自定义字段映射
Map<String, String> fieldMapping = Map.of(
    "姓名", "name",
    "年龄", "age",
    "部门", "department"
);

List<User> users = reader.read(User.class, fieldMapping);
```

## 实用场景

### 场景1：Excel转CSV

```java
// 从URL读取Excel，转换为本地CSV
try (ExcelReader reader = ExcelReader.openUrl("https://example.com/data.xlsx")) {
    List<Map<String, Object>> data = reader.sheet(0).read();
    
    CsvWriter writer = CsvWriter.create("converted.csv");
    writer.write(data);
}
```

### 场景2：CSV转Excel

```java
// 从本地CSV读取，转换为Excel
CsvReader csvReader = CsvReader.open("data.csv");
List<Map<String, Object>> data = csvReader.read();

try (ExcelWriter writer = ExcelWriter.create("converted.xlsx")) {
    writer.sheet("数据").write(data);
    writer.flush();
}
```

### 场景3：合并多个数据源

```java
try (ExcelWriter writer = ExcelWriter.create("merged.xlsx")) {
    // 从URL读取数据
    List<Map<String, Object>> urlData = 
        ExcelReader.openUrl("https://example.com/data1.xlsx")
                   .sheet(0).read();
    
    // 从本地读取数据
    List<Map<String, Object>> localData = 
        CsvReader.open("data2.csv").read();
    
    // 写入到不同Sheet
    writer.sheet("在线数据").write(urlData);
    writer.sheet("本地数据").write(localData);
    writer.flush();
}
```

## 工具类说明

### BeanConverter

```java

```

提供Bean与Map之间的转换功能，支持@ExcelColumn注解：

```java
// Bean转Map
List<Map<String, Object>> maps = BeanConverter.beansToMaps(userList, User.class);

// Map转Bean（自动映射）
List<User> users = BeanConverter.mapsToBeans(maps, User.class, null);

// Map转Bean（自定义字段映射）
Map<String, String> mapping = Map.of("姓名", "name", "年龄", "age");
List<User> users = BeanConverter.mapsToBeans(maps, User.class, mapping);
```

### CellHelper

```java

```

提供Excel单元格的读写操作：

```java
// 读取单元格值
String value = CellHelper.getValue(cell);

// 设置单元格值
CellHelper.setValue(cell, "文本值");
CellHelper.setValue(cell, 123);
CellHelper.setValue(cell, new Date());
```

### CsvHelper

```java

```

提供CSV格式的解析和格式化功能：

```java
// 读取CSV行（处理引号内的换行符）
String line = CsvHelper.readCsvLine(bufferedReader);

// 解析CSV行
List<String> fields = CsvHelper.parseLine(line);

// 格式化CSV行
String csvLine = CsvHelper.formatLine(valueList);

// 格式化单个值（自动处理引号转义）
String formattedValue = CsvHelper.formatValue(value);
```

## 注意事项

1. **资源管理**：ExcelReader和ExcelWriter需要关闭资源，建议使用try-with-resources
2. **URL访问**：从URL读取时，确保网络连接正常且URL可访问
3. **文件格式**：
   - Excel支持：`.xls` (Excel 2003) 和 `.xlsx` (Excel 2007+)
   - CSV编码：默认使用UTF-8编码
4. **写操作限制**：从URL打开的文件仅支持读取，不支持写入操作
5. **大文件处理**：处理大文件时注意内存使用，考虑分批读取

## 性能建议

- 对于大量数据操作，优先使用CSV格式（性能更好）
- 使用URL读取时，考虑缓存到本地以提高效率
- Excel写入完成后务必调用`flush()`方法

## 更多示例

查看测试文件：`UrlReadExample.java`

