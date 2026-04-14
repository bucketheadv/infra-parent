package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;

public class GoSpringConfigProperty {
    private final String key;
    private final String value;
    private final PsiElement psiElement;

    public GoSpringConfigProperty(String key, String value, PsiElement psiElement) {
        this.key = key;
        this.value = value;
        this.psiElement = psiElement;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
