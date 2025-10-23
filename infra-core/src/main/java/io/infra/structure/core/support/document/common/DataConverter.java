package io.infra.structure.core.support.document.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据转换工具类
 * 用于处理行数据和Map之间的转换
 *
 * @author sven
 * Created on 2025/10/23
 */
public class DataConverter {

    /**
     * 将值列表转换为Map（使用表头作为key）
     *
     * @param headers 表头列表
     * @param values  值列表
     * @return Map，key为表头，value为对应的值
     */
    public static Map<String, Object> toMap(List<String> headers, List<String> values) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            rowData.put(headers.get(i), value);
        }
        return rowData;
    }

    /**
     * 验证自定义表头是否有效
     *
     * @param customHeaders 自定义表头
     * @throws IllegalArgumentException 如果表头为空
     */
    public static void validateHeaders(List<String> customHeaders) {
        if (customHeaders == null || customHeaders.isEmpty()) {
            throw new IllegalArgumentException("自定义表头不能为空");
        }
    }
}
