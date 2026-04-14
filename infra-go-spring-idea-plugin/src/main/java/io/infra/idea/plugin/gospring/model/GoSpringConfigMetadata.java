package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class GoSpringConfigMetadata {
    private final String key;
    private final String typeName;
    private final String defaultValue;
    private final String sourceLabel;
    private final PsiElement psiElement;

    public GoSpringConfigMetadata(String key,
                                  @Nullable String typeName,
                                  @Nullable String defaultValue,
                                  String sourceLabel,
                                  PsiElement psiElement) {
        this.key = key;
        this.typeName = typeName;
        this.defaultValue = defaultValue;
        this.sourceLabel = sourceLabel;
        this.psiElement = psiElement;
    }

    public String getKey() {
        return key;
    }

    public @Nullable String getTypeName() {
        return typeName;
    }

    public @Nullable String getDefaultValue() {
        return defaultValue;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
