package io.infra.structure.core.tool;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.validator.internal.engine.messageinterpolation.DefaultLocaleResolver;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

/**
 * @author sven
 * Created on 2025/1/12 15:04
 */
public class ValidationTool {
    /**
     * 参数校验
     * @param objectName
     * @param obj
     * @throws BindException
     */
    public static void validate(String objectName, Object obj) throws BindException {
        Locale locale = LocaleContextHolder.getLocale();
        Set<ConstraintViolation<Object>> result;
        try (ValidatorFactory validatorFactory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ResourceBundleMessageInterpolator(Collections.emptySet(), locale, new DefaultLocaleResolver(), false))
                .buildValidatorFactory()) {
            result = validatorFactory.getValidator().validate(obj);
        }
        if (CollectionUtils.isNotEmpty(result)) {
            BindingResult br = new MapBindingResult(new HashMap<>(), objectName);
            for (ConstraintViolation<Object> cv : result) {
                br.rejectValue(cv.getPropertyPath().toString(), cv.getMessageTemplate(), cv.getMessage());
            }
            throw new BindException(br);
        }
    }
}
