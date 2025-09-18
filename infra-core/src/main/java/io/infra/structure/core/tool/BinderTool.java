package io.infra.structure.core.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.NoSuchElementException;

/**
 * @author sven
 * Created on 2025/1/12 16:06
 */
@Slf4j
public class BinderTool {
    public static <T> T bind(Environment environment, String prefix, Class<T> clazz) {
        try {
            return Binder.get(environment).bind(prefix, clazz).get();
        } catch (NoSuchElementException e) {
            log.debug("未检测到 {} 配置，类 {} 配置项可能未绑定成功", prefix, clazz.getName());
        }
        return null;
    }
}
