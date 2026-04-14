package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoSpringBeanDefinition {
    private final String beanName;
    private final List<String> providedTypes;
    private final PsiElement psiElement;

    public GoSpringBeanDefinition(@Nullable String beanName, List<String> providedTypes, PsiElement psiElement) {
        this.beanName = beanName;
        this.providedTypes = List.copyOf(providedTypes);
        this.psiElement = psiElement;
    }

    public @Nullable String getBeanName() {
        return beanName;
    }

    public List<String> getProvidedTypes() {
        return providedTypes;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
