package io.infra.idea.plugin.gospring.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoSpringPropertyLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!GoSpringPsi.isSupportedConfigFile(element.getContainingFile())) {
            return null;
        }
        IProperty property = PsiTreeUtil.getParentOfType(element, Property.class, false);
        if (property == null || property.getKey() == null || !property.getKey().equals(element.getText())) {
            return null;
        }
        PsiElement[] targets = GoSpringConfigKeyNavigationSupport.findPreferredTargetsForGutter(element.getProject(), property.getKey());
        if (targets == null || targets.length == 0) {
            return null;
        }
        return NavigationGutterIconBuilder
                .create(AllIcons.General.Locate)
                .setTargets(List.of(targets))
                .setTooltipText("跳转到 Go 配置使用点或模块定义")
                .createLineMarkerInfo(element);
    }
}
