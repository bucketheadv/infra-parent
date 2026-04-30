package io.infra.idea.plugin.gospring.inspection;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights only the navigable field segment in go-spring value tags.
 */
public class GoSpringValueTagAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiElement stringLiteral = GoSpringPsi.findStringLiteral(element);
        if (stringLiteral == null || !stringLiteral.equals(element)) {
            return;
        }
        for (GoSpringPsi.TagMatch match : GoSpringPsi.findTagMatches(stringLiteral)) {
            if (match.getKind() != GoSpringPsi.ReferenceKind.VALUE) {
                continue;
            }
            if (match.getRange().isEmpty()) {
                continue;
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(match.getRange().shiftRight(element.getTextRange().getStartOffset()))
                    .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                    .create();
        }
    }
}
