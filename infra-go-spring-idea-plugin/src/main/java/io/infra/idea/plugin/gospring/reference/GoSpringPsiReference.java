package io.infra.idea.plugin.gospring.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringBeanDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringBeanInjectionUsage;
import io.infra.idea.plugin.gospring.model.GoSpringConfigProperty;
import io.infra.idea.plugin.gospring.model.GoSpringConfigUsage;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GoSpringPsiReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private final GoSpringPsi.TagMatch match;

    public GoSpringPsiReference(PsiElement element, GoSpringPsi.TagMatch match) {
        super(element, match.getRange(), true);
        this.match = match;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();
        if (match.getKind() == GoSpringPsi.ReferenceKind.AUTOWIRE) {
            int offset = myElement.getTextRange().getStartOffset() + match.getRange().getStartOffset();
            GoSpringPsi.AutowireNavigation navigation = GoSpringPsi.findAutowireNavigationAtOffset(myElement, offset);
            if (navigation != null) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(
                        myElement.getProject(),
                        navigation.getTypeName(),
                        navigation.getBeanName(),
                        navigation.isWildcard(),
                        navigation.getExplicitBeanNames())) {
                    if (property.getPsiElement() != null) {
                        results.add(new PsiElementResolveResult(property.getPsiElement()));
                    }
                }
                if (results.isEmpty()) {
                    Collection<GoSpringBeanDefinition> definitions = navigation.getBeanName() == null || navigation.getBeanName().isBlank()
                            ? GoSpringIndex.findBeanDefinitionsByType(myElement.getProject(), navigation.getTypeName())
                            : GoSpringIndex.findBeanDefinitions(myElement.getProject(), new GoSpringBeanInjectionUsage(
                            navigation.getBeanName(),
                            navigation.getTypeName(),
                            GoSpringBeanInjectionUsage.Kind.FIELD,
                            myElement
                    ));
                    for (GoSpringBeanDefinition definition : definitions) {
                        if (definition.getPsiElement() != null) {
                            results.add(new PsiElementResolveResult(definition.getPsiElement()));
                        }
                    }
                }
            }
        } else {
            Collection<GoSpringConfigUsage> usages = GoSpringIndex.findConfigUsagesAt(myElement);
            if (usages.isEmpty()) {
                usages = List.of(new GoSpringConfigUsage(match.getValue(), List.of(match.getValue()), false, myElement));
            }
            for (GoSpringConfigUsage usage : usages) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(myElement.getProject(), usage)) {
                    if (property.getPsiElement() != null) {
                        results.add(new PsiElementResolveResult(property.getPsiElement()));
                    }
                }
            }
        }
        return results.toArray(new ResolveResult[0]);
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<Object> variants = new ArrayList<>();
        if (match.getKind() == GoSpringPsi.ReferenceKind.AUTOWIRE) {
            int offset = myElement.getTextRange().getStartOffset() + match.getRange().getStartOffset();
            GoSpringPsi.AutowireNavigation navigation = GoSpringPsi.findAutowireNavigationAtOffset(myElement, offset);
            if (navigation != null) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(
                        myElement.getProject(),
                        navigation.getTypeName(),
                        navigation.getBeanName(),
                        navigation.isWildcard(),
                        navigation.getExplicitBeanNames())) {
                    variants.add(LookupElementBuilder.create(property.getKey()));
                }
                if (!variants.isEmpty()) {
                    return variants.toArray();
                }
            }
            for (GoSpringBeanDefinition definition : GoSpringIndex.findBeanDefinitions(myElement.getProject(), match.getValue())) {
                if (definition.getBeanName() != null && !definition.getBeanName().isBlank()) {
                    variants.add(LookupElementBuilder.create(definition.getBeanName()));
                }
            }
            return variants.toArray();
        }
        Collection<GoSpringConfigUsage> usages = GoSpringIndex.findConfigUsagesAt(myElement);
        if (usages.isEmpty()) {
            usages = List.of(new GoSpringConfigUsage(match.getValue(), List.of(match.getValue()), false, myElement));
        }
        for (GoSpringConfigUsage usage : usages) {
            for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(myElement.getProject(), usage)) {
                variants.add(LookupElementBuilder.create(property.getKey()));
            }
        }
        return variants.toArray();
    }
}
