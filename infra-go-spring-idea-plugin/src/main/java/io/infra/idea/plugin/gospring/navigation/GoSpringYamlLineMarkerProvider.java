package io.infra.idea.plugin.gospring.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class GoSpringYamlLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!GoSpringPsi.isSupportedConfigFile(element.getContainingFile()) || !(element.getContainingFile() instanceof YAMLFile)) {
            return;
        }
        YAMLKeyValue keyValue = com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);
        if (keyValue == null || keyValue.getKey() != element) {
            return;
        }
        String propertyKey = buildYamlPropertyKey(keyValue);
        if (propertyKey == null || propertyKey.isBlank()) {
            return;
        }
        Collection<PsiElement> usages = new java.util.LinkedHashSet<>(GoSpringIndex.findValueUsages(element.getProject(), propertyKey));
        for (GoSpringExternalConfigDefinition definition : GoSpringIndex.findExternalConfigDefinitions(element.getProject(), propertyKey)) {
            if (definition.getPsiElement() != null) {
                usages.add(definition.getPsiElement());
            }
        }
        if (usages.isEmpty()) {
            return;
        }
        List<PsiElement> targets = new ArrayList<>(usages);
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.General.Locate)
                .setTargets(targets)
                .setTooltipText("跳转到 Go 配置使用点或模块定义");
        result.add(builder.createLineMarkerInfo(element));
    }

    private static String buildYamlPropertyKey(YAMLKeyValue keyValue) {
        LinkedList<String> segments = new LinkedList<>();
        YAMLKeyValue cursor = keyValue;
        while (cursor != null) {
            String segment = cursor.getKeyText();
            if (segment == null || segment.isBlank()) {
                return null;
            }
            segments.addFirst(segment);
            cursor = com.intellij.psi.util.PsiTreeUtil.getParentOfType(cursor, YAMLKeyValue.class, true);
        }
        return String.join(".", segments);
    }
}
