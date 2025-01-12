package org.infra.structure.core.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * @author sven
 * Created on 2025/1/12 16:06
 */
@Slf4j
public class BinderTool {
    public static <T> T bind(Environment environment, String prefix, Class<T> clazz) {
        try {
            return Binder.get(environment).bind(prefix, clazz).get();
        } catch (Exception e) {
            log.warn("绑定配置 {} 失败, Error: {}", prefix, e.getMessage());
        }
        return null;
    }
}
