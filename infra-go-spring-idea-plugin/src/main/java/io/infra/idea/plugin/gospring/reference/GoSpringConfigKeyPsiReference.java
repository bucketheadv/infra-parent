package io.infra.idea.plugin.gospring.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringConfigProperty;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GoSpringConfigKeyPsiReference extends PsiPolyVariantReferenceBase<PsiElement> {
    public enum Kind {
        GROUP,
        INSTANCE,
        FIELD
    }

    private final String propertyKey;
    private final String instanceName;
    private final Kind kind;

    public GoSpringConfigKeyPsiReference(PsiElement element,
                                         TextRange range,
                                         String propertyKey,
                                         String instanceName,
                                         Kind kind) {
        super(element, range, false);
        this.propertyKey = propertyKey;
        this.instanceName = instanceName;
        this.kind = kind;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();
        if (kind == Kind.GROUP) {
            for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(myElement.getProject(), propertyKey)) {
                results.add(new PsiElementResolveResult(definition.getPsiElement()));
            }
            return results.toArray(new ResolveResult[0]);
        }
        if (kind == Kind.INSTANCE) {
            for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(myElement.getProject(), propertyKey)) {
                for (PsiElement usage : GoSpringIndex.findAutowireUsages(myElement.getProject(), definition, instanceName)) {
                    results.add(new PsiElementResolveResult(usage));
                }
            }
            return results.toArray(new ResolveResult[0]);
        }
        Set<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringExternalConfigDefinition definition : GoSpringIndex.findExternalConfigDefinitions(myElement.getProject(), propertyKey)) {
            if (definition.getPsiElement() != null) {
                targets.add(definition.getPsiElement());
            }
        }
        for (PsiElement target : targets) {
            results.add(new PsiElementResolveResult(target));
        }
        return results.toArray(new ResolveResult[0]);
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<Object> variants = new ArrayList<>();
        if (kind == Kind.GROUP) {
            for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(myElement.getProject(), propertyKey)) {
                variants.add(LookupElementBuilder.create(definition.getGroupPrefix()));
            }
            return variants.toArray();
        }
        if (kind == Kind.INSTANCE) {
            variants.add(LookupElementBuilder.create(instanceName));
            return variants.toArray();
        }
        Collection<GoSpringExternalConfigDefinition> definitions = GoSpringIndex.findExternalConfigDefinitions(myElement.getProject(), propertyKey);
        for (GoSpringExternalConfigDefinition definition : definitions) {
            variants.add(LookupElementBuilder.create(definition.getRelativeKey()));
        }
        return variants.toArray();
    }
}
