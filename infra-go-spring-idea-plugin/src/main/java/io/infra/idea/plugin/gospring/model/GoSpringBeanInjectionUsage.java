package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class GoSpringBeanInjectionUsage {
    public enum Kind {
        FIELD,
        PARAMETER
    }

    private final String beanName;
    private final String typeName;
    private final Kind kind;
    private final PsiElement psiElement;

    public GoSpringBeanInjectionUsage(@Nullable String beanName, @Nullable String typeName, Kind kind, PsiElement psiElement) {
        this.beanName = beanName;
        this.typeName = typeName;
        this.kind = kind;
        this.psiElement = psiElement;
    }

    public @Nullable String getBeanName() {
        return beanName;
    }

    public @Nullable String getTypeName() {
        return typeName;
    }

    public Kind getKind() {
        return kind;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
