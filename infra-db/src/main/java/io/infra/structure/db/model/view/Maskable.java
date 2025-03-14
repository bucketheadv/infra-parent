package io.infra.structure.db.model.view;

import com.mybatisflex.annotation.ColumnMask;
import com.mybatisflex.core.mask.MaskManager;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Field;

/**
 * @author sven
 * Created on 2025/1/9 17:41
 */
public interface Maskable<T> {
    /**
     * 数据脱敏，返回当前对象拷贝出来的新对象
     * @return
     */
    default T toMask() {
        try {
            return mask();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private T mask() throws Exception {
        var ins = getClass().getConstructor().newInstance();
        BeanUtils.copyProperties(this, ins);
        for (Field declaredField : ins.getClass().getDeclaredFields()) {
            var mask = declaredField.getAnnotation(ColumnMask.class);
            if (mask == null) {
                continue;
            }
            declaredField.setAccessible(true);
            declaredField.set(ins, MaskManager.mask(mask.value(), declaredField.get(ins)));
        }
        return (T) ins;
    }
}
