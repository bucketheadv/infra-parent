package io.infra.idea.plugin.gospring.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringBeanDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringBeanInjectionUsage;
import io.infra.idea.plugin.gospring.model.GoSpringConfigProperty;
import io.infra.idea.plugin.gospring.model.GoSpringConfigUsage;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GoSpringLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!GoSpringPsi.isGoFile(element)) {
            return;
        }

        Collection<GoSpringConfigUsage> configUsages = GoSpringIndex.findConfigUsagesForAnchor(element);
        if (!configUsages.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringConfigUsage usage : configUsages) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(element.getProject(), usage)) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
            }
            addMarker(element, result, targets, "跳转到配置");
        }

        Collection<GoSpringExternalConfigDefinition> externalDefinitions = GoSpringIndex.findExternalConfigDefinitionsForAnchor(element);
        if (!externalDefinitions.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringExternalConfigDefinition definition : externalDefinitions) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(element.getProject(), definition)) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
            }
            addMarker(element, result, targets, "跳转到项目配置");
        }

        Collection<GoSpringGroupDefinition> groupDefinitions = GoSpringIndex.findGroupDefinitionsForAnchor(element);
        if (!groupDefinitions.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringGroupDefinition definition : groupDefinitions) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(element.getProject(), definition)) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
            }
            addMarker(element, result, targets, "跳转到 Group 配置");
        }

        Collection<GoSpringBeanInjectionUsage> injectionUsages = GoSpringIndex.findBeanInjectionUsagesForAnchor(element);
        if (!injectionUsages.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringBeanInjectionUsage usage : injectionUsages) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(
                        element.getProject(),
                        usage.getTypeName(),
                        usage.getBeanName(),
                        false,
                        List.of())) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
                if (targets.isEmpty()) {
                    for (GoSpringBeanDefinition definition : GoSpringIndex.findBeanDefinitions(element.getProject(), usage)) {
                        if (definition.getPsiElement() != null) {
                            targets.add(definition.getPsiElement());
                        }
                    }
                }
            }
            addMarker(element, result, targets, "跳转到 Bean 定义");
        }

        GoSpringBeanDefinition definition = GoSpringIndex.findBeanDefinitionForAnchor(element);
        if (definition != null) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            collectDefinitionUsages(element, definition, targets);
            addMarker(element, result, targets, "跳转到 Bean 使用点");
        }
    }

    private static void collectDefinitionUsages(PsiElement sourceElement,
                                                GoSpringBeanDefinition definition,
                                                Set<PsiElement> targets) {
        if (definition.getBeanName() != null && !definition.getBeanName().isBlank()) {
            targets.addAll(GoSpringIndex.findAutowireUsages(sourceElement.getProject(), definition.getBeanName()));
        }
        for (String typeName : definition.getProvidedTypes()) {
            targets.addAll(GoSpringIndex.findAutowireUsagesByType(sourceElement.getProject(), typeName));
        }
    }

    private static void addMarker(PsiElement anchor,
                                  Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                  Collection<PsiElement> targets,
                                  String tooltip) {
        if (anchor == null || targets == null || targets.isEmpty()) {
            return;
        }
        List<PsiElement> targetList = new ArrayList<>(targets);
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.General.Locate)
                .setTargets(targetList)
                .setTooltipText(tooltip);
        result.add(builder.createLineMarkerInfo(anchor));
    }
}
