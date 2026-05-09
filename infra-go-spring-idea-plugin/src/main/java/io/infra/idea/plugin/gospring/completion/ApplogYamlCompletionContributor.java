package io.infra.idea.plugin.gospring.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import io.infra.idea.plugin.gospring.navigation.InfraGoApplogYamlNavigation;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

/**
 * 专用 YAML 通道补全 {@code applog} 配置（避免仅依赖 {@code language="any"} 的 contributor 与 PSI 提交时机问题）。
 */
public class ApplogYamlCompletionContributor extends CompletionContributor {
    public ApplogYamlCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                PsiFile file = parameters.getOriginalFile();
                if (!GoSpringPsi.isApplogConfigFile(file)) {
                    return;
                }
                Document doc = parameters.getEditor() == null ? null : parameters.getEditor().getDocument();
                String text = doc != null ? doc.getText() : file.getText();
                if (text == null) {
                    return;
                }
                InfraGoApplogYamlNavigation.addApplogYamlValueCompletions(file, text, parameters.getOffset(), result);
            }
        });
    }
}
