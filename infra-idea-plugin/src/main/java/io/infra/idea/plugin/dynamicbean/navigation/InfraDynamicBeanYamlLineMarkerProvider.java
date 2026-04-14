package io.infra.idea.plugin.dynamicbean.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import io.infra.idea.plugin.icons.InfraDynamicBeanIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Adds reverse navigation from YAML config keys to matching injection points.
 */
public class InfraDynamicBeanYamlLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        YAMLKeyValue keyValue = findYamlKeyValueAtAnchor(element);
        if (keyValue == null || !(keyValue.getContainingFile() instanceof YAMLFile)) {
            return null;
        }
        String propertyKey = buildYamlPropertyKey(keyValue);
        if (propertyKey == null) {
            return null;
        }
        InfraDynamicBeanDefinition definition = InfraDynamicBeanIndex.findByNavigationLocation(
                element.getProject(),
                keyValue.getContainingFile(),
                propertyKey
        );
        if (definition == null || !keyValue.equals(definition.getNavigationProperty().getPsiElement())) {
            return null;
        }
        Collection<PsiElement> targets = InfraDynamicBeanInjectionSearch.findTargets(element.getProject(), definition);
        if (targets.isEmpty()) {
            return null;
        }
        return NavigationGutterIconBuilder
                .create(InfraDynamicBeanIcons.TO_INJECTION)
                .setTargets(List.copyOf(targets))
                .setTooltipText("跳转到动态 Bean 注入位置: " + definition.getBeanName())
                .createLineMarkerInfo(element);
    }

    private static @Nullable YAMLKeyValue findYamlKeyValueAtAnchor(PsiElement element) {
        PsiElement cursor = element;
        while (cursor != null && !(cursor instanceof YAMLFile)) {
            if (cursor instanceof YAMLKeyValue keyValue) {
                PsiElement anchor = keyValue.getContainingFile().findElementAt(keyValue.getTextRange().getStartOffset());
                return anchor == element ? keyValue : null;
            }
            cursor = cursor.getParent();
        }
        return null;
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
            cursor = findParentYamlKeyValue(cursor);
        }
        return String.join(".", segments);
    }

    private static @Nullable YAMLKeyValue findParentYamlKeyValue(@Nullable PsiElement element) {
        PsiElement cursor = element == null ? null : element.getParent();
        while (cursor != null && !(cursor instanceof YAMLFile)) {
            if (cursor instanceof YAMLKeyValue keyValue) {
                return keyValue;
            }
            cursor = cursor.getParent();
        }
        return null;
    }
}
