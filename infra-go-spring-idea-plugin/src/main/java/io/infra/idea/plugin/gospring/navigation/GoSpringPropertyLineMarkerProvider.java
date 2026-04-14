package io.infra.idea.plugin.gospring.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GoSpringPropertyLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!GoSpringPsi.isSupportedConfigFile(element.getContainingFile())) {
            return;
        }
        IProperty property = PsiTreeUtil.getParentOfType(element, Property.class, false);
        if (property == null || property.getKey() == null) {
            return;
        }
        if (!property.getKey().equals(element.getText())) {
            return;
        }
        Collection<PsiElement> usages = new java.util.LinkedHashSet<>(GoSpringIndex.findValueUsages(element.getProject(), property.getKey()));
        for (GoSpringExternalConfigDefinition definition : GoSpringIndex.findExternalConfigDefinitions(element.getProject(), property.getKey())) {
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
}
