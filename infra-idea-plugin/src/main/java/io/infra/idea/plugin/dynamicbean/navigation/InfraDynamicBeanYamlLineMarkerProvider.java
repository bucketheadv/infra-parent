package io.infra.idea.plugin.dynamicbean.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import io.infra.idea.plugin.icons.InfraDynamicBeanIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Adds reverse navigation from YAML config keys to matching injection points.
 */
public class InfraDynamicBeanYamlLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);
        if (keyValue == null) {
            return;
        }
        PsiElement anchor = keyValue.getKey() == null ? keyValue : keyValue.getKey();
        if (element.getTextRange().getStartOffset() != anchor.getTextRange().getStartOffset()) {
            return;
        }
        String propertyKey = buildYamlPropertyKey(keyValue);
        if (propertyKey == null) {
            return;
        }
        InfraDynamicBeanDefinition definition = InfraDynamicBeanIndex.findByNavigationLocation(
                element.getProject(),
                keyValue.getContainingFile(),
                propertyKey
        );
        if (definition == null || !keyValue.equals(definition.getNavigationProperty().getPsiElement())) {
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
        result.add(builder.createLineMarkerInfo(anchor));
    }

    private static @Nullable String buildYamlPropertyKey(YAMLKeyValue keyValue) {
        LinkedList<String> segments = new LinkedList<>();
        YAMLKeyValue cursor = keyValue;
        while (cursor != null) {
            String segment = cursor.getKeyText();
            if (segment == null || segment.isBlank()) {
                return null;
            }
            segments.addFirst(segment);
            cursor = PsiTreeUtil.getParentOfType(cursor, YAMLKeyValue.class, true);
        }
        return String.join(".", segments);
    }
}
