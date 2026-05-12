package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoSpringBeanDefinition {
    private final String beanName;
    private final String factoryName;
    private final List<String> providedTypes;
    private final PsiElement psiElement;

    public GoSpringBeanDefinition(@Nullable String beanName,
                                  @Nullable String factoryName,
                                  List<String> providedTypes,
                                  PsiElement psiElement) {
        this.beanName = beanName;
        this.factoryName = factoryName;
        this.providedTypes = List.copyOf(providedTypes);
        this.psiElement = psiElement;
    }

    public @Nullable String getBeanName() {
        return beanName;
    }

    public @Nullable String getFactoryName() {
        return factoryName;
    }

    public List<String> getProvidedTypes() {
        return providedTypes;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
