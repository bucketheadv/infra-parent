package io.infra.idea.plugin.dynamicbean.highlight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import io.infra.idea.plugin.dynamicbean.psi.InfraDynamicBeanPsi;
import io.infra.idea.plugin.dynamicbean.psi.InfraInjectionPoint;
import org.jetbrains.annotations.NotNull;

/**
 * Filters false-positive Spring autowire warnings when the bean exists in infra dynamic properties.
 */
public class InfraDynamicBeanAutowireHighlightFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, PsiFile file) {
        if (file == null || !isAutowireWarning(highlightInfo)) {
            return true;
        }
        PsiElement element = file.findElementAt(Math.max(0, highlightInfo.getStartOffset()));
        if (element == null) {
            return true;
        }
        InfraInjectionPoint injectionPoint = InfraDynamicBeanPsi.getInjectionPoint(element);
        if (injectionPoint == null || injectionPoint.getTypeName() == null) {
            return true;
        }
        InfraDynamicBeanDefinition definition = resolveDefinition(file, injectionPoint);
        return definition == null;
    }

    private InfraDynamicBeanDefinition resolveDefinition(PsiFile file, InfraInjectionPoint injectionPoint) {
        String qualifierValue = injectionPoint.getQualifierValue();
        if (qualifierValue != null && !qualifierValue.isBlank()) {
            return InfraDynamicBeanIndex.findByName(file.getProject(), qualifierValue, injectionPoint.getTypeName());
        }
        return InfraDynamicBeanIndex.findPrimary(file.getProject(), injectionPoint.getTypeName());
    }

    private boolean isAutowireWarning(HighlightInfo highlightInfo) {
        String description = highlightInfo.getDescription();
        if (description == null || description.isBlank()) {
            return false;
        }
        return (description.contains("Could not autowire") && description.contains("No beans of"))
                || (description.contains("无法自动装配") && description.contains("找不到") && description.contains("类型的 Bean"));
    }
}
