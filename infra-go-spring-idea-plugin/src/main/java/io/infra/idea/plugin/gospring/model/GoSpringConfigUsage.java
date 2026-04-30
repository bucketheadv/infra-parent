package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;

import java.util.List;

public class GoSpringConfigUsage {
    private final String declaredKey;
    private final List<String> effectiveKeys;
    private final boolean prefixMatch;
    private final PsiElement psiElement;
    private final String ownerTypeName;

    public GoSpringConfigUsage(String declaredKey, List<String> effectiveKeys, boolean prefixMatch, PsiElement psiElement) {
        this(declaredKey, effectiveKeys, prefixMatch, psiElement, null);
    }

    public GoSpringConfigUsage(String declaredKey,
                               List<String> effectiveKeys,
                               boolean prefixMatch,
                               PsiElement psiElement,
                               String ownerTypeName) {
        this.declaredKey = declaredKey;
        this.effectiveKeys = List.copyOf(effectiveKeys);
        this.prefixMatch = prefixMatch;
        this.psiElement = psiElement;
        this.ownerTypeName = ownerTypeName;
    }

    public String getDeclaredKey() {
        return declaredKey;
    }

    public List<String> getEffectiveKeys() {
        return effectiveKeys;
    }

    public boolean isPrefixMatch() {
        return prefixMatch;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }

    public String getOwnerTypeName() {
        return ownerTypeName;
    }
}
