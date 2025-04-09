package io.infra.structure.core.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author liuqinglin
 * Date: 2025/4/2 上午11:09
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiData<T> {
    /**
     * 错误码
     */
    private int code;

    /**
     * 错误描述
     */
    private String msg;

    /**
     * 数据
     */
    private T data;

    public static <T> ApiData<T> ok(T data) {
        return ApiData.<T>builder()
                .code(0)
                .msg("success")
                .data(data)
                .build();
    }

    public static <T> ApiData<T> ok() {
        return ok(null);
    }

    public static <T> ApiData<T> fail(int code, String msg) {
        return ApiData.<T>builder()
                .code(code)
                .msg(msg)
                .build();
    }
}
