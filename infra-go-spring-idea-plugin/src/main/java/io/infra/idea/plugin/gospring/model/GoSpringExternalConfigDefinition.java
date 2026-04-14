package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;

public class GoSpringExternalConfigDefinition {
    private final String groupPrefix;
    private final String relativeKey;
    private final boolean prefixMatch;
    private final PsiElement psiElement;

    public GoSpringExternalConfigDefinition(String groupPrefix, String relativeKey, boolean prefixMatch, PsiElement psiElement) {
        this.groupPrefix = groupPrefix;
        this.relativeKey = relativeKey;
        this.prefixMatch = prefixMatch;
        this.psiElement = psiElement;
    }

    public String getGroupPrefix() {
        return groupPrefix;
    }

    public String getRelativeKey() {
        return relativeKey;
    }

    public boolean isPrefixMatch() {
        return prefixMatch;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
