package io.infra.idea.plugin.gospring.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.List;

public class GoSpringYamlLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!GoSpringPsi.isSupportedConfigFile(element.getContainingFile()) || !(element.getContainingFile() instanceof YAMLFile)) {
            return null;
        }
        YAMLKeyValue keyValue = findYamlKeyValueAtAnchor(element);
        if (keyValue == null) {
            return null;
        }
        String propertyKey = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(keyValue);
        if (propertyKey == null || propertyKey.isBlank()) {
            return null;
        }
        PsiElement[] targets = GoSpringConfigKeyNavigationSupport.findPreferredTargetsForGutter(element.getProject(), propertyKey);
        if (targets == null || targets.length == 0) {
            return null;
        }
        return NavigationGutterIconBuilder
                .create(AllIcons.General.Locate)
                .setTargets(List.of(targets))
                .setTooltipText("跳转到 Go 配置使用点或模块定义")
                .setAlignment(GutterIconRenderer.Alignment.LEFT)
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
}
