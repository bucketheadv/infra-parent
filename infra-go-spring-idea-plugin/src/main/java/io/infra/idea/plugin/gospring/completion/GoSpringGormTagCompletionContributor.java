package io.infra.idea.plugin.gospring.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import io.infra.idea.plugin.gospring.inspection.GoSpringGormTagSupport;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Completion support for gorm struct tags in Go source files.
 */
public class GoSpringGormTagCompletionContributor extends CompletionContributor {
    public GoSpringGormTagCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                PsiElement position = parameters.getPosition();
                PsiElement host = position.getParent() != null ? position.getParent() : position;
                GoSpringGormTagSupport.CompletionContext completionContext =
                        GoSpringGormTagSupport.completionContext(host, parameters.getOffset());
                if (completionContext == null) {
                    return;
                }
                List<String> variants = completionContext.getKind() == GoSpringGormTagSupport.CompletionKind.TYPE_VALUE
                        ? GoSpringGormTagSupport.typeCompletions()
                        : GoSpringGormTagSupport.directiveCompletions();
                String prefix = completionContext.getPrefix();
                CompletionResultSet completionResultSet = prefix.isBlank()
                        ? result
                        : result.withPrefixMatcher(prefix);
                for (String variant : variants) {
                    completionResultSet.addElement(LookupElementBuilder.create(variant));
                }
            }
        });
    }
}
