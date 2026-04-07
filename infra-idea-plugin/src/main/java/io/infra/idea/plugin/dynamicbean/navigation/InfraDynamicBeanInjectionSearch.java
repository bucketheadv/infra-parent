package io.infra.idea.plugin.dynamicbean.navigation;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import io.infra.idea.plugin.dynamicbean.psi.InfraDynamicBeanPsi;
import io.infra.idea.plugin.dynamicbean.psi.InfraInjectionPoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds injection sites that resolve to a dynamic bean definition.
 */
public final class InfraDynamicBeanInjectionSearch {
    private InfraDynamicBeanInjectionSearch() {
    }

    public static Collection<PsiElement> findTargets(Project project, InfraDynamicBeanDefinition definition) {
        Set<PsiElement> targets = new LinkedHashSet<>();
        PsiManager psiManager = PsiManager.getInstance(project);

        collectJavaTargets(project, psiManager, definition, targets);
        collectKotlinTargets(project, psiManager, definition, targets);

        return targets;
    }

    private static void collectJavaTargets(Project project,
                                           PsiManager psiManager,
                                           InfraDynamicBeanDefinition definition,
                                           Set<PsiElement> targets) {
        for (VirtualFile file : FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                continue;
            }
            for (PsiField field : PsiTreeUtil.findChildrenOfType(psiFile, PsiField.class)) {
                addIfMatches(field.getNameIdentifier(), definition, targets);
            }
            for (PsiParameter parameter : PsiTreeUtil.findChildrenOfType(psiFile, PsiParameter.class)) {
                addIfMatches(parameter.getNameIdentifier(), definition, targets);
            }
        }
    }

    private static void collectKotlinTargets(Project project,
                                             PsiManager psiManager,
                                             InfraDynamicBeanDefinition definition,
                                             Set<PsiElement> targets) {
        for (VirtualFile file : FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
            PsiFile psiFile = psiManager.findFile(file);
            if (!(psiFile instanceof KtFile ktFile)) {
                continue;
            }
            for (KtParameter parameter : PsiTreeUtil.findChildrenOfType(ktFile, KtParameter.class)) {
                addIfMatches(parameter.getNameIdentifier(), definition, targets);
            }
            for (KtProperty property : PsiTreeUtil.findChildrenOfType(ktFile, KtProperty.class)) {
                addIfMatches(property.getNameIdentifier(), definition, targets);
            }
        }
    }

    private static void addIfMatches(PsiElement anchor, InfraDynamicBeanDefinition definition, Set<PsiElement> targets) {
        if (anchor == null) {
            return;
        }
        InfraInjectionPoint injectionPoint = InfraDynamicBeanPsi.getInjectionPoint(anchor);
        if (injectionPoint != null && matches(definition, injectionPoint)) {
            targets.add(anchor);
        }
    }

    private static boolean matches(InfraDynamicBeanDefinition definition, @NotNull InfraInjectionPoint injectionPoint) {
        if (injectionPoint.getTypeName() == null || !definition.matchesType(injectionPoint.getTypeName())) {
            return false;
        }
        String qualifierValue = injectionPoint.getQualifierValue();
        if (qualifierValue != null && !qualifierValue.isBlank()) {
            return definition.getBeanName().equals(qualifierValue);
        }
        return definition.isPrimary();
    }
}
