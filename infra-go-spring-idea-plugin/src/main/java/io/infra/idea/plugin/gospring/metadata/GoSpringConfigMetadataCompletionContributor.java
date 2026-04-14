package io.infra.idea.plugin.gospring.metadata;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import io.infra.idea.plugin.gospring.model.GoSpringConfigMetadata;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class GoSpringConfigMetadataCompletionContributor extends CompletionContributor {
    public GoSpringConfigMetadataCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                addConfigMetadataCompletions(parameters, result);
            }
        });
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiFile file = parameters.getOriginalFile();
        if (!GoSpringPsi.isSupportedConfigFile(file)) {
            return;
        }
        if (addConfigMetadataCompletions(parameters, result)) {
            result.stopHere();
            return;
        }
        super.fillCompletionVariants(parameters, result);
    }

    private static boolean addConfigMetadataCompletions(@NotNull CompletionParameters parameters,
                                                        @NotNull CompletionResultSet result) {
        PsiFile file = parameters.getOriginalFile();
        Document document = parameters.getEditor().getDocument();
        String text = document.getText();
        if (!GoSpringPsi.isSupportedConfigFile(file)) {
            return false;
        }
        if (file instanceof com.intellij.lang.properties.psi.PropertiesFile) {
            return addPropertiesCompletions(file, text, parameters.getOffset(), result);
        }
        if (file instanceof org.jetbrains.yaml.psi.YAMLFile) {
            return addYamlCompletions(file, text, parameters.getOffset(), result);
        }
        return false;
    }

    private static boolean addPropertiesCompletions(PsiFile file, String text, int offset, CompletionResultSet result) {
        String prefix = GoSpringConfigMetadataSupport.extractPropertiesKeyPrefix(text, offset);
        if (prefix == null) {
            return false;
        }
        boolean added = false;
        CompletionResultSet prefixed = result.withPrefixMatcher(prefix);
        for (Map.Entry<String, GoSpringConfigMetadata> entry :
                GoSpringConfigMetadataSupport.collectPropertiesMetadata(file.getProject(), file, prefix).entrySet()) {
            prefixed.addElement(createLookup(entry.getValue(), entry.getKey()));
            added = true;
        }
        return added;
    }

    private static boolean addYamlCompletions(PsiFile file, String text, int offset, CompletionResultSet result) {
        GoSpringConfigMetadataSupport.YamlCompletionContext context =
                GoSpringConfigMetadataSupport.extractYamlCompletionContext(file, text, offset);
        if (context == null) {
            return false;
        }
        boolean added = false;
        if (context.isFlattenedKey()) {
            String prefix = context.getTypedPrefix() == null ? "" : context.getTypedPrefix();
            CompletionResultSet prefixed = result.withPrefixMatcher(prefix);
            for (Map.Entry<String, GoSpringConfigMetadata> entry :
                    GoSpringConfigMetadataSupport.collectPropertiesMetadata(file.getProject(), file, prefix).entrySet()) {
                prefixed.addElement(createLookup(entry.getValue(), entry.getKey(), true));
                added = true;
            }
            return added;
        }
        CompletionResultSet prefixed = result.withPrefixMatcher(context.getTypedPrefix() == null ? "" : context.getTypedPrefix());
        for (Map.Entry<String, GoSpringConfigMetadata> entry :
                GoSpringConfigMetadataSupport.collectYamlMetadata(file.getProject(), file, context.getParentKey(), context.getTypedPrefix()).entrySet()) {
            prefixed.addElement(createLookup(entry.getValue(), entry.getKey(), false));
            added = true;
        }
        return added;
    }

    private static LookupElementBuilder createLookup(GoSpringConfigMetadata metadata, String lookupString) {
        return createLookup(metadata, lookupString, false);
    }

    private static LookupElementBuilder createLookup(GoSpringConfigMetadata metadata,
                                                     String lookupString,
                                                     boolean expandToYamlMapping) {
        StringBuilder tailText = new StringBuilder("  Go-Spring");
        if (metadata.getDefaultValue() != null && !metadata.getDefaultValue().isBlank()) {
            tailText.append("  default: ").append(metadata.getDefaultValue());
        }
        LookupElementBuilder builder = LookupElementBuilder.create(lookupString)
                .withTailText(tailText.toString(), true);
        if (metadata.getTypeName() != null && !metadata.getTypeName().isBlank()) {
            builder = builder.withTypeText(metadata.getTypeName(), true);
        }
        if (expandToYamlMapping && lookupString.contains(".")) {
            builder = builder.withInsertHandler(new YamlKeyInsertHandler());
        }
        return builder;
    }

    private static final class YamlKeyInsertHandler implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
            String lookupString = item.getLookupString();
            if (lookupString.isBlank() || !lookupString.contains(".")) {
                return;
            }
            Document document = context.getDocument();
            CharSequence chars = document.getCharsSequence();
            int lineNumber = document.getLineNumber(context.getStartOffset());
            int lineStart = document.getLineStartOffset(lineNumber);
            int lineEnd = document.getLineEndOffset(lineNumber);

            int contentStart = lineStart;
            while (contentStart < lineEnd && Character.isWhitespace(chars.charAt(contentStart))) {
                contentStart++;
            }
            String indent = chars.subSequence(lineStart, contentStart).toString();
            String replacement = buildYamlSnippet(lookupString, indent);
            document.replaceString(contentStart, lineEnd, replacement);

            int caretOffset = contentStart + replacement.length();
            context.getEditor().getCaretModel().moveToOffset(caretOffset);
            context.setTailOffset(caretOffset);
        }

        private static String buildYamlSnippet(String key, String indent) {
            String[] segments = key.split("\\.");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(indent);
                builder.append("  ".repeat(i));
                builder.append(segments[i]);
                builder.append(':');
                if (i == segments.length - 1) {
                    builder.append(' ');
                }
            }
            return builder.toString();
        }
    }
}
