package io.infra.structure.core.support.document.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excel/CSV列注解，用于标注Bean字段的显示名称
 * <p>
 * 使用示例：
 * <pre>
 * public class Employee {
 *     &#064;ExcelColumn("姓名")
 *     private String name;
 *     
 *     &#064;ExcelColumn("年龄")
 *     private Integer age;
 * }
 * </pre>
 *
 * @author sven
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {
    /**
     * 列的显示名称
     */
    String value();
}

