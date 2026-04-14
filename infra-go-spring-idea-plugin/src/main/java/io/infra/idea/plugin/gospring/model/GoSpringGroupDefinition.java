package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;

import java.util.List;

public class GoSpringGroupDefinition {
    private final String groupPrefix;
    private final List<String> providedTypes;
    private final PsiElement psiElement;

    public GoSpringGroupDefinition(String groupPrefix, List<String> providedTypes, PsiElement psiElement) {
        this.groupPrefix = groupPrefix;
        this.providedTypes = List.copyOf(providedTypes);
        this.psiElement = psiElement;
    }

    public String getGroupPrefix() {
        return groupPrefix;
    }

    public List<String> getProvidedTypes() {
        return providedTypes;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
