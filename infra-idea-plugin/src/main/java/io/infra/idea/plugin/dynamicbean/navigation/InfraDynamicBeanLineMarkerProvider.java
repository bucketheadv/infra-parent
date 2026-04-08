package io.infra.idea.plugin.dynamicbean.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import io.infra.idea.plugin.dynamicbean.psi.InfraDynamicBeanPsi;
import io.infra.idea.plugin.dynamicbean.psi.InfraInjectionPoint;
import io.infra.idea.plugin.icons.InfraDynamicBeanIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Adds gutter navigation from bean injection sites to the backing properties definition.
 */
public class InfraDynamicBeanLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        InfraInjectionPoint injectionPoint = InfraDynamicBeanPsi.getInjectionPoint(element);
        if (injectionPoint == null || injectionPoint.getTypeName() == null) {
            return;
        }
        InfraDynamicBeanDefinition definition = injectionPoint.getQualifierValue() == null
                ? InfraDynamicBeanIndex.findPrimary(element.getProject(), injectionPoint.getTypeName())
                : InfraDynamicBeanIndex.findByName(element.getProject(), injectionPoint.getQualifierValue(), injectionPoint.getTypeName());
        if (definition == null || definition.getNavigationProperty().getPsiElement() == null) {
            return;
        }
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(InfraDynamicBeanIcons.TO_CONFIG)
                .setTarget(definition.getNavigationProperty().getPsiElement())
                .setTooltipText("跳转到动态 Bean 配置: " + definition.getBeanName());
        result.add(builder.createLineMarkerInfo(injectionPoint.getAnchor()));
    }
}
