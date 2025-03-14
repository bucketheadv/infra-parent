package io.infra.structure.core.tool;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author sven
 * Created on 2025/1/12 14:33
 */
public class BeanTool {

    public static <T> T copyAs(Object source, Class<T> clazz) {
        if (source == null) {
            return null;
        }
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, obj);
            return obj;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> copyList(Collection<?> c, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(c)) {
            return result;
        }
        for (Object o : c) {
            result.add(copyAs(o, clazz));
        }
        return result;
    }
}
