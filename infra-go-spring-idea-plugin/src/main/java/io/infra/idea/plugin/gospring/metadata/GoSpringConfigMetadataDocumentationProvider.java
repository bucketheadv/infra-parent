package io.infra.idea.plugin.gospring.metadata;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.infra.idea.plugin.gospring.model.GoSpringConfigMetadata;
import io.infra.idea.plugin.gospring.navigation.InfraGoApplogYamlDocs;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.Nullable;

public class GoSpringConfigMetadataDocumentationProvider extends AbstractDocumentationProvider {
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(Editor editor,
                                                              PsiFile file,
                                                              @Nullable PsiElement contextElement,
                                                              int targetOffset) {
        return GoSpringConfigMetadataSupport.findDocumentationTarget(editor, file, contextElement, targetOffset);
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        PsiElement ref = originalElement != null ? originalElement : element;
        PsiFile file = ref.getContainingFile();
        if (GoSpringPsi.isApplogConfigFile(file)) {
            String key = GoSpringConfigMetadataSupport.resolveConfigKey(ref);
            if (key != null) {
                String applogDoc = InfraGoApplogYamlDocs.generateHtml(key);
                if (applogDoc != null) {
                    return applogDoc;
                }
            }
        }
        GoSpringConfigMetadata metadata = GoSpringConfigMetadataSupport.resolveMetadata(ref);
        return metadata == null ? null : GoSpringConfigMetadataSupport.buildDocumentation(metadata);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        PsiElement ref = originalElement != null ? originalElement : element;
        PsiFile file = ref.getContainingFile();
        if (GoSpringPsi.isApplogConfigFile(file)) {
            String key = GoSpringConfigMetadataSupport.resolveConfigKey(ref);
            if (key != null) {
                String info = InfraGoApplogYamlDocs.generateQuickInfo(key);
                if (info != null) {
                    return info;
                }
            }
        }
        GoSpringConfigMetadata metadata = GoSpringConfigMetadataSupport.resolveMetadata(ref);
        if (metadata == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(metadata.getKey());
        if (metadata.getTypeName() != null && !metadata.getTypeName().isBlank()) {
            builder.append(" : ").append(metadata.getTypeName());
        }
        if (metadata.getDefaultValue() != null && !metadata.getDefaultValue().isBlank()) {
            builder.append(" = ").append(metadata.getDefaultValue());
        }
        return builder.toString();
    }
}
