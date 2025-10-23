package io.infra.structure.core.support.document.common;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

/**
 * Bean转换工具类
 * 支持Bean与Map之间的相互转换，自动识别@ExcelColumn注解
 *
 * @author sven
 * Created on 2025/10/23
 */
@Slf4j
public class BeanConverter {

    /**
     * 将Bean列表转换为Map列表
     */
    public static <T> List<Map<String, Object>> beansToMaps(List<T> dataList, Class<T> clazz) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        try {
            Field[] fields = clazz.getDeclaredFields();

            for (T obj : dataList) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Field field : fields) {
                    field.setAccessible(true);
                    // 如果字段有@ExcelColumn注解，使用注解的值作为key；否则使用字段名
                    String key = getColumnName(field);
                    map.put(key, field.get(obj));
                }
                mapList.add(map);
            }
        } catch (Exception e) {
            log.error("转换Bean为Map失败", e);
            throw new RuntimeException("转换Bean为Map失败", e);
        }
        return mapList;
    }

    /**
     * 将Map列表转换为Bean列表
     */
    public static <T> List<T> mapsToBeans(List<Map<String, Object>> mapList, Class<T> clazz, Map<String, String> fieldMapping) {
        List<T> result = new ArrayList<>();
        try {
            Field[] fields = clazz.getDeclaredFields();
            // 构建字段映射表：列名 -> Field
            Map<String, Field> columnToField = new HashMap<>();
            for (Field field : fields) {
                field.setAccessible(true);
                // 优先使用@ExcelColumn注解的值，其次使用字段名
                String columnName = getColumnName(field);
                columnToField.put(columnName, field);
                // 同时保留字段名映射，兼容无注解的情况
                if (!columnName.equals(field.getName())) {
                    columnToField.put(field.getName(), field);
                }
            }

            for (Map<String, Object> map : mapList) {
                T obj = clazz.getDeclaredConstructor().newInstance();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    try {
                        String key = entry.getKey();
                        Field field = null;

                        // 1. 如果有外部传入的字段映射，优先使用
                        if (fieldMapping != null && fieldMapping.containsKey(key)) {
                            String mappedFieldName = fieldMapping.get(key);
                            field = columnToField.get(mappedFieldName);
                        }
                        
                        // 2. 直接通过列名查找（支持@Column注解）
                        if (field == null) {
                            field = columnToField.get(key);
                        }

                        if (field != null) {
                            Object value = convertValue(entry.getValue(), field.getType());
                            field.set(obj, value);
                        } else {
                            log.debug("字段不存在: {}", key);
                        }
                    } catch (Exception e) {
                        log.debug("设置字段失败: {}", entry.getKey(), e);
                    }
                }
                result.add(obj);
            }
        } catch (Exception e) {
            log.error("转换Map为Bean失败", e);
            throw new RuntimeException("转换Map为Bean失败", e);
        }
        return result;
    }

    /**
     * 获取字段的列名（优先使用@ExcelColumn注解）
     */
    private static String getColumnName(Field field) {
        ExcelColumn column = field.getAnnotation(ExcelColumn.class);
        return column != null ? column.value() : field.getName();
    }

    /**
     * 类型转换
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }

        String strValue = value.toString();
        try {
            if (targetType == String.class) {
                return strValue;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(strValue);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(strValue);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(strValue);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(strValue);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(strValue);
            } else if (targetType == BigDecimal.class) {
                return new BigDecimal(strValue);
            } else {
                return value;
            }
        } catch (Exception e) {
            log.warn("类型转换失败: {} -> {}", value, targetType.getSimpleName());
            return null;
        }
    }
}

