package io.infra.idea.plugin.gospring.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringConfigMetadata;
import io.infra.idea.plugin.gospring.navigation.GoSpringConfigKeyNavigationSupport;
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
    private final int navigationOffsetInKey;

    public GoSpringConfigKeyPsiReference(PsiElement element,
                                         TextRange range,
                                         String propertyKey,
                                         String instanceName,
                                         Kind kind,
                                         int navigationOffsetInKey) {
        super(element, range, false);
        this.propertyKey = propertyKey;
        this.instanceName = instanceName;
        this.kind = kind;
        this.navigationOffsetInKey = navigationOffsetInKey;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();
        PsiElement[] resolved = GoSpringConfigKeyNavigationSupport.findTargets(
                myElement.getProject(),
                propertyKey,
                navigationOffsetInKey
        );
        Set<PsiElement> targets = new LinkedHashSet<>();
        if (resolved != null) {
            java.util.Collections.addAll(targets, resolved);
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
            if (variants.isEmpty()) {
                for (GoSpringConfigMetadata metadata : GoSpringIndex.getConfigMetadata(myElement.getProject())) {
                    String key = metadata.getKey();
                    int separator = key.indexOf('.');
                    variants.add(LookupElementBuilder.create(separator > 0 ? key.substring(0, separator) : key));
                }
            }
            return variants.toArray();
        }
        if (kind == Kind.INSTANCE) {
            if (instanceName != null && !instanceName.isBlank()) {
                variants.add(LookupElementBuilder.create(instanceName));
            }
            return variants.toArray();
        }
        Collection<GoSpringExternalConfigDefinition> definitions = GoSpringIndex.findExternalConfigDefinitions(myElement.getProject(), propertyKey);
        for (GoSpringExternalConfigDefinition definition : definitions) {
            variants.add(LookupElementBuilder.create(definition.getRelativeKey()));
        }
        if (variants.isEmpty()) {
            for (GoSpringConfigMetadata metadata : GoSpringIndex.getConfigMetadata(myElement.getProject())) {
                if (!metadata.getKey().startsWith(propertyKey)) {
                    continue;
                }
                variants.add(LookupElementBuilder.create(metadata.getKey())
                        .withTypeText(metadata.getTypeName(), true)
                        .withTailText(metadata.getDefaultValue() == null || metadata.getDefaultValue().isBlank()
                                ? "  Go-Spring"
                                : "  default: " + metadata.getDefaultValue(), true));
            }
        }
        return variants.toArray();
    }
}
