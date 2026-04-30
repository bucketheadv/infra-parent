package io.infra.idea.plugin.gospring.reference;

import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.infra.idea.plugin.gospring.index.GoSpringGormQueryIndex;
import io.infra.idea.plugin.gospring.navigation.GoSpringNavigationTargetElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference from gorm:"column:xxx" values to mapped query usages.
 */
public class GoSpringGormColumnTagPsiReference extends PsiPolyVariantReferenceBase<PsiElement> implements HighlightedReference {
    private final String columnValue;

    public GoSpringGormColumnTagPsiReference(PsiElement element, TextRange range, String columnValue) {
        super(element, range, true);
        this.columnValue = columnValue == null ? "" : columnValue.trim();
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();
        for (PsiElement target : GoSpringGormQueryIndex.findUsageTargetsForColumnInLiteral(myElement.getProject(), myElement, columnValue)) {
            results.add(new PsiElementResolveResult(GoSpringNavigationTargetElement.wrap(target)));
        }
        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }
}
