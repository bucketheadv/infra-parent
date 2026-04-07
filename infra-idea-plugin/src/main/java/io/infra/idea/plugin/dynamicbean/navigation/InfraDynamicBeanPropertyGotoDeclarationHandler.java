package io.infra.idea.plugin.dynamicbean.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import org.jetbrains.annotations.Nullable;

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
        if (elementType != PropertiesTokenTypes.KEY_CHARACTERS) {
            return null;
        }
        IProperty property = PsiTreeUtil.getParentOfType(sourceElement, Property.class, false);
        if (property == null) {
            return null;
        }
        InfraDynamicBeanDefinition definition = InfraDynamicBeanIndex.findByNavigationProperty(sourceElement.getProject(), property);
        if (definition == null) {
            return null;
        }
        Collection<PsiElement> targets = InfraDynamicBeanInjectionSearch.findTargets(sourceElement.getProject(), definition);
        if (targets.isEmpty()) {
            return null;
        }
        return targets.toArray(new PsiElement[0]);
    }
}
