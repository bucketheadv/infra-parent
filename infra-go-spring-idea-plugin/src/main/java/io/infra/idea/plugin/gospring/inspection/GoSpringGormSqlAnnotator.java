package io.infra.idea.plugin.gospring.inspection;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.gospring.index.GoSpringGormQueryIndex;
import io.infra.idea.plugin.gospring.model.GoSpringGormQueryUsage;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights GORM SQL strings and mapped model fields inside query methods.
 */
public class GoSpringGormSqlAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        for (TextRange keywordRange : GoSpringGormQueryIndex.findKeywordRanges(element)) {
            TextRange absolute = keywordRange.shiftRight(element.getTextRange().getStartOffset());
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(absolute)
                    .textAttributes(DefaultLanguageHighlighterColors.KEYWORD)
                    .create();
        }
        for (GoSpringGormQueryUsage usage : GoSpringGormQueryIndex.findUsagesInLiteral(element)) {
            TextRange absolute = usage.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(absolute)
                    .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                    .create();
        }
    }
}
