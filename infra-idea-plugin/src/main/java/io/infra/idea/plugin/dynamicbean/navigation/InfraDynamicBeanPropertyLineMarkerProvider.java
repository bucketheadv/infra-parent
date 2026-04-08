package io.infra.idea.plugin.dynamicbean.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.lang.properties.IProperty;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import io.infra.idea.plugin.icons.InfraDynamicBeanIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Adds reverse navigation from a dynamic bean property to all matching injection points.
 */
public class InfraDynamicBeanPropertyLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof IProperty property)) {
            return;
        }
        InfraDynamicBeanDefinition definition = InfraDynamicBeanIndex.findByNavigationProperty(element.getProject(), property);
        if (definition == null) {
            return;
        }
        if (property.getPsiElement() == null || !property.getPsiElement().equals(definition.getNavigationProperty().getPsiElement())) {
            return;
        }
        Collection<PsiElement> targets = InfraDynamicBeanInjectionSearch.findTargets(element.getProject(), definition);
        if (targets.isEmpty()) {
            return;
        }
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(InfraDynamicBeanIcons.TO_INJECTION)
                .setTargets(targets)
                .setTooltipText("跳转到动态 Bean 注入位置: " + definition.getBeanName());
        result.add(builder.createLineMarkerInfo(element));
    }
}
