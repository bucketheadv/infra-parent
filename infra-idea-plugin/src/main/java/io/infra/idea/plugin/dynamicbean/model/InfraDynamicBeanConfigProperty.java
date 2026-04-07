package io.infra.idea.plugin.dynamicbean.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Unified config property abstraction for both .properties and .yml sources.
 */
public class InfraDynamicBeanConfigProperty {
    private final String key;
    private final String value;
    private final PsiElement psiElement;

    public InfraDynamicBeanConfigProperty(String key, String value, PsiElement psiElement) {
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

    public PsiFile getContainingFile() {
        return psiElement == null ? null : psiElement.getContainingFile();
    }
}
