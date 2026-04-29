package io.infra.idea.plugin.gospring.reference;

import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.infra.idea.plugin.gospring.index.GoSpringGormQueryIndex;
import io.infra.idea.plugin.gospring.model.GoSpringGormFieldDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGormQueryUsage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference from GORM SQL column tokens to model struct fields.
 */
public class GoSpringGormSqlPsiReference extends PsiPolyVariantReferenceBase<PsiElement> implements HighlightedReference {
    private final GoSpringGormQueryUsage usage;

    public GoSpringGormSqlPsiReference(PsiElement element, GoSpringGormQueryUsage usage) {
        super(element, usage.getRangeInElement(), true);
        this.usage = usage;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        GoSpringGormFieldDefinition definition = GoSpringGormQueryIndex.findFieldDefinition(myElement.getProject(), usage);
        if (definition == null || definition.getPsiElement() == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        return new ResolveResult[]{new PsiElementResolveResult(definition.getPsiElement())};
    }

    @Override
    public Object @NotNull [] getVariants() {
        GoSpringGormFieldDefinition definition = GoSpringGormQueryIndex.findFieldDefinition(myElement.getProject(), usage);
        if (definition == null) {
            return EMPTY_ARRAY;
        }
        List<Object> variants = new ArrayList<>();
        variants.add(LookupElementBuilder.create(definition.getColumnName()).withTypeText(definition.getFieldName(), true));
        return variants.toArray();
    }
}
