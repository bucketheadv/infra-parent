package io.infra.idea.plugin.dynamicbean.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collection;

/**
 * Enables cmd-click navigation from dynamic bean properties keys to matching injection sites.
 */
public class InfraDynamicBeanPropertyGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        IElementType elementType = sourceElement.getNode() == null ? null : sourceElement.getNode().getElementType();
        if (elementType == PropertiesTokenTypes.KEY_CHARACTERS) {
            IProperty property = PsiTreeUtil.getParentOfType(sourceElement, Property.class, false);
            if (property == null) {
                return null;
            }
            return findTargets(sourceElement.getProject(), property.getPsiElement() == null ? null : property.getPsiElement().getContainingFile(), property.getKey());
        }
        PsiFile containingFile = sourceElement.getContainingFile();
        if (!(containingFile instanceof YAMLFile)) {
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
        String propertyKey = buildYamlPropertyKey(keyValue);
        InfraDynamicBeanDefinition definition = InfraDynamicBeanIndex.findByNavigationLocation(sourceElement.getProject(), keyValue.getContainingFile(), propertyKey);
        if (definition == null || !keyValue.equals(definition.getNavigationProperty().getPsiElement())) {
            return null;
        }
        return findTargets(sourceElement.getProject(), definition);
    }

    private PsiElement @Nullable [] findTargets(com.intellij.openapi.project.Project project, @Nullable PsiFile sourceFile, @Nullable String key) {
        InfraDynamicBeanDefinition definition = InfraDynamicBeanIndex.findByNavigationLocation(project, sourceFile, key);
        return findTargets(project, definition);
    }

    private PsiElement @Nullable [] findTargets(com.intellij.openapi.project.Project project, @Nullable InfraDynamicBeanDefinition definition) {
        if (definition == null) {
            return null;
        }
        Collection<PsiElement> targets = InfraDynamicBeanInjectionSearch.findTargets(project, definition);
        if (targets.isEmpty()) {
            return null;
        }
        return targets.toArray(new PsiElement[0]);
    }

    private static @Nullable String buildYamlPropertyKey(YAMLKeyValue keyValue) {
        java.util.LinkedList<String> segments = new java.util.LinkedList<>();
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
