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

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights GORM SQL strings and mapped model fields inside query methods.
 */
public class GoSpringGormSqlAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        List<TextRange> keywordRanges = new ArrayList<>(GoSpringGormQueryIndex.findKeywordRanges(element));
        for (TextRange keywordRange : keywordRanges) {
            TextRange absolute = keywordRange.shiftRight(element.getTextRange().getStartOffset());
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(absolute)
                    .textAttributes(DefaultLanguageHighlighterColors.KEYWORD)
                    .create();
        }
        for (GoSpringGormQueryUsage usage : GoSpringGormQueryIndex.findUsagesInLiteral(element)) {
            if (overlapsAny(usage.getRangeInElement(), keywordRanges)) {
                continue;
            }
            TextRange absolute = usage.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(absolute)
                    .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                    .create();
        }
    }

    private static boolean overlapsAny(TextRange range, List<TextRange> ranges) {
        for (TextRange candidate : ranges) {
            if (range.intersects(candidate)) {
                return true;
            }
        }
        return false;
    }
}
