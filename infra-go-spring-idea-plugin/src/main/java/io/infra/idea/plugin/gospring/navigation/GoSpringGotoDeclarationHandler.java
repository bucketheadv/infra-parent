package io.infra.idea.plugin.gospring.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringBeanDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringBeanInjectionUsage;
import io.infra.idea.plugin.gospring.model.GoSpringConfigProperty;
import io.infra.idea.plugin.gospring.model.GoSpringConfigUsage;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class GoSpringGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }

        PsiElement[] goTargets = findGoTargets(sourceElement, offset);
        if (goTargets != null) {
            return goTargets;
        }

        PsiFile containingFile = sourceElement.getContainingFile();
        if (containingFile instanceof com.intellij.lang.properties.psi.PropertiesFile) {
            return null;
        }

        return findYamlTargets(sourceElement, offset);
    }

    private PsiElement @Nullable [] findGoTargets(PsiElement sourceElement, int offset) {
        if (!GoSpringPsi.isGoFile(sourceElement)) {
            return null;
        }

        GoSpringPsi.AutowireNavigation autowireNavigation = GoSpringPsi.findAutowireNavigationAtOffset(sourceElement, offset);
        if (autowireNavigation != null) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(
                    sourceElement.getProject(),
                    autowireNavigation.getTypeName(),
                    autowireNavigation.getBeanName(),
                    autowireNavigation.isWildcard(),
                    autowireNavigation.getExplicitBeanNames())) {
                if (property.getPsiElement() != null) {
                    targets.add(property.getPsiElement());
                }
            }
            if (targets.isEmpty()) {
                if (autowireNavigation.getBeanName() != null && !autowireNavigation.getBeanName().isBlank()) {
                    GoSpringBeanInjectionUsage usage = new GoSpringBeanInjectionUsage(
                            autowireNavigation.getBeanName(),
                            autowireNavigation.getTypeName(),
                            GoSpringBeanInjectionUsage.Kind.FIELD,
                            sourceElement
                    );
                    for (GoSpringBeanDefinition definition : GoSpringIndex.findBeanDefinitions(sourceElement.getProject(), usage)) {
                        if (definition.getPsiElement() != null) {
                            targets.add(definition.getPsiElement());
                        }
                    }
                } else {
                    for (GoSpringBeanDefinition definition : GoSpringIndex.findBeanDefinitionsByType(sourceElement.getProject(), autowireNavigation.getTypeName())) {
                        if (definition.getBeanName() != null
                                && autowireNavigation.isWildcard()
                                && autowireNavigation.getExplicitBeanNames().contains(definition.getBeanName())) {
                            continue;
                        }
                        if (definition.getPsiElement() != null) {
                            targets.add(definition.getPsiElement());
                        }
                    }
                }
            }
            if (!targets.isEmpty()) {
                return targets.toArray(new PsiElement[0]);
            }
        }

        GoSpringPsi.TagMatch tagMatch = GoSpringPsi.findTagMatchAtOffset(sourceElement, offset);
        if (tagMatch != null) {
            List<PsiElement> targets = new ArrayList<>();
            if (tagMatch.getKind() == GoSpringPsi.ReferenceKind.VALUE) {
                Collection<GoSpringConfigUsage> usages = GoSpringIndex.findConfigUsagesAt(sourceElement);
                if (!usages.isEmpty()) {
                    for (GoSpringConfigUsage usage : usages) {
                        for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(sourceElement.getProject(), usage)) {
                            if (property.getPsiElement() != null) {
                                targets.add(property.getPsiElement());
                            }
                        }
                    }
                } else {
                    Collection<GoSpringExternalConfigDefinition> definitions = GoSpringIndex.findExternalConfigDefinitionsAt(sourceElement);
                    if (!definitions.isEmpty()) {
                        for (GoSpringExternalConfigDefinition definition : definitions) {
                            for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(sourceElement.getProject(), definition)) {
                                if (property.getPsiElement() != null) {
                                    targets.add(property.getPsiElement());
                                }
                            }
                        }
                    } else {
                        GoSpringConfigUsage fallback = new GoSpringConfigUsage(tagMatch.getValue(), List.of(tagMatch.getValue()), false, sourceElement);
                        for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(sourceElement.getProject(), fallback)) {
                            if (property.getPsiElement() != null) {
                                targets.add(property.getPsiElement());
                            }
                        }
                    }
                }
            }
            if (!targets.isEmpty()) {
                return targets.toArray(new PsiElement[0]);
            }
        }

        Collection<GoSpringBeanInjectionUsage> injectionUsages = GoSpringIndex.findBeanInjectionUsagesAt(sourceElement);
        if (!injectionUsages.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringBeanInjectionUsage usage : injectionUsages) {
                for (GoSpringBeanDefinition definition : GoSpringIndex.findBeanDefinitions(sourceElement.getProject(), usage)) {
                    if (definition.getPsiElement() != null) {
                        targets.add(definition.getPsiElement());
                    }
                }
            }
            return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
        }

        Collection<GoSpringGroupDefinition> groupDefinitions = GoSpringIndex.findGroupDefinitionsAt(sourceElement);
        if (!groupDefinitions.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringGroupDefinition definition : groupDefinitions) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(sourceElement.getProject(), definition)) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
            }
            return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
        }

        GoSpringPsi.BeanDefinitionMatch beanDefinitionMatch = GoSpringPsi.findBeanDefinitionAtOffset(sourceElement, offset);
        if (beanDefinitionMatch != null) {
            Set<PsiElement> usages = new LinkedHashSet<>();
            for (GoSpringBeanDefinition definition : GoSpringIndex.findBeanDefinitions(sourceElement.getProject(), beanDefinitionMatch.getBeanName())) {
                collectDefinitionUsages(sourceElement, definition, usages);
            }
            return usages.isEmpty() ? null : usages.toArray(new PsiElement[0]);
        }

        GoSpringBeanDefinition definition = GoSpringIndex.findBeanDefinitionAt(sourceElement);
        if (definition == null) {
            return null;
        }
        Set<PsiElement> usages = new LinkedHashSet<>();
        collectDefinitionUsages(sourceElement, definition, usages);
        return usages.isEmpty() ? null : usages.toArray(new PsiElement[0]);
    }

    private PsiElement @Nullable [] findPropertiesTargets(PsiElement sourceElement, int offset) {
        PsiFile containingFile = sourceElement.getContainingFile();
        if (!GoSpringPsi.isSupportedConfigFile(containingFile) || !(containingFile instanceof com.intellij.lang.properties.psi.PropertiesFile)) {
            return null;
        }
        IElementType elementType = sourceElement.getNode() == null ? null : sourceElement.getNode().getElementType();
        if (elementType != PropertiesTokenTypes.KEY_CHARACTERS) {
            return null;
        }
        IProperty property = PsiTreeUtil.getParentOfType(sourceElement, Property.class, false);
        if (property == null || property.getKey() == null) {
            return null;
        }
        int offsetInKey = sourceElement.getTextRange() == null ? -1 : offset - sourceElement.getTextRange().getStartOffset();
        return findConfigKeyTargets(sourceElement, property.getKey(), offsetInKey);
    }

    private PsiElement @Nullable [] findYamlTargets(PsiElement sourceElement, int offset) {
        PsiFile containingFile = sourceElement.getContainingFile();
        if (!GoSpringPsi.isSupportedConfigFile(containingFile) || !(containingFile instanceof YAMLFile)) {
            return null;
        }
        YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(sourceElement, YAMLKeyValue.class, false);
        if (keyValue == null && offset >= 0) {
            PsiElement elementAtOffset = containingFile.findElementAt(offset);
            if (elementAtOffset != null) {
                keyValue = PsiTreeUtil.getParentOfType(elementAtOffset, YAMLKeyValue.class, false);
            }
        }
        if (keyValue == null) {
            return null;
        }
        PsiElement anchor = keyValue.getKey() == null ? keyValue : keyValue.getKey();
        if (!anchor.getTextRange().containsOffset(offset)) {
            return null;
        }
        String propertyKey = buildYamlPropertyKey(keyValue);
        if (propertyKey == null) {
            return null;
        }
        int offsetInKey = offset - anchor.getTextRange().getStartOffset();
        return findConfigKeyTargets(sourceElement, propertyKey, offsetInKey);
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

    private PsiElement @Nullable [] findConfigKeyTargets(PsiElement sourceElement, String propertyKey, int offsetInKey) {
        Set<PsiElement> targets = new LinkedHashSet<>();
        if (offsetInKey >= 0) {
            for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(sourceElement.getProject(), propertyKey)) {
                if (isGroupPrefixOffset(definition.getGroupPrefix(), offsetInKey) && definition.getPsiElement() != null) {
                    targets.add(definition.getPsiElement());
                }
            }
            if (!targets.isEmpty()) {
                return targets.toArray(new PsiElement[0]);
            }
            String instanceName = resolveInstanceName(propertyKey);
            if (isInstanceOffset(propertyKey, offsetInKey) && instanceName != null) {
                for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(sourceElement.getProject(), propertyKey)) {
                    targets.addAll(GoSpringIndex.findAutowireUsages(sourceElement.getProject(), definition, instanceName));
                }
            }
            if (!targets.isEmpty()) {
                return targets.toArray(new PsiElement[0]);
            }
        }
        targets.addAll(GoSpringIndex.findValueUsages(sourceElement.getProject(), propertyKey));
        for (GoSpringExternalConfigDefinition definition : GoSpringIndex.findExternalConfigDefinitions(sourceElement.getProject(), propertyKey)) {
            if (definition.getPsiElement() != null) {
                targets.add(definition.getPsiElement());
            }
        }
        return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
    }

    private boolean isGroupPrefixOffset(String groupPrefix, int offsetInKey) {
        return groupPrefix != null && !groupPrefix.isBlank() && offsetInKey >= 0 && offsetInKey < groupPrefix.length();
    }

    private boolean isInstanceOffset(String propertyKey, int offsetInKey) {
        int first = propertyKey == null ? -1 : propertyKey.indexOf('.');
        int second = propertyKey == null ? -1 : propertyKey.indexOf('.', first + 1);
        int third = propertyKey == null ? -1 : propertyKey.indexOf('.', second + 1);
        if (first < 0 || second < 0 || third < 0) {
            return false;
        }
        return offsetInKey > second && offsetInKey < third;
    }

    private @Nullable String resolveInstanceName(String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return null;
        }
        int first = propertyKey.indexOf('.');
        int second = propertyKey.indexOf('.', first + 1);
        int third = propertyKey.indexOf('.', second + 1);
        if (first < 0 || second < 0 || third < 0 || third <= second + 1) {
            return null;
        }
        return propertyKey.substring(second + 1, third);
    }
}
