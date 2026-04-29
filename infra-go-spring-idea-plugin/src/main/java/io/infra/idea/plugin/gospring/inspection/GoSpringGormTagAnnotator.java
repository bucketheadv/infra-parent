package io.infra.idea.plugin.gospring.inspection;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Runtime highlighter for malformed GORM tags in Go struct definitions.
 */
public class GoSpringGormTagAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        for (GoSpringGormTagSupport.HighlightTarget highlightTarget : GoSpringGormTagSupport.collectHighlights(element)) {
            TextRange absoluteRange = highlightTarget.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(absoluteRange)
                    .textAttributes(switch (highlightTarget.getKind()) {
                        case DIRECTIVE -> DefaultLanguageHighlighterColors.KEYWORD;
                        case VALUE -> DefaultLanguageHighlighterColors.STRING;
                        case TYPE_VALUE -> DefaultLanguageHighlighterColors.CLASS_NAME;
                    })
                    .create();
        }
        for (GoSpringGormTagSupport.Issue issue : GoSpringGormTagSupport.validate(element)) {
            TextRange absoluteRange = issue.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
            holder.newAnnotation(HighlightSeverity.ERROR, issue.getMessage())
                    .range(absoluteRange)
                    .create();
        }
    }
}
