package io.infra.idea.plugin.gospring.reference;

import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;

final class GoSpringHighlightedPsiReference extends GoSpringPsiReference implements HighlightedReference {
    GoSpringHighlightedPsiReference(PsiElement element, GoSpringPsi.TagMatch match) {
        super(element, match);
    }
}
