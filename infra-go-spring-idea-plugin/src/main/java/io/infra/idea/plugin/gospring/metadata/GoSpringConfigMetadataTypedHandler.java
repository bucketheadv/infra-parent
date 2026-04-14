package io.infra.idea.plugin.gospring.metadata;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class GoSpringConfigMetadataTypedHandler extends TypedHandlerDelegate {
    @Override
    public @NotNull Result checkAutoPopup(char charTyped,
                                          @NotNull Project project,
                                          @NotNull Editor editor,
                                          @NotNull PsiFile file) {
        if (!Character.isLetterOrDigit(charTyped) && charTyped != '.' && charTyped != '-') {
            return Result.CONTINUE;
        }
        if (!io.infra.idea.plugin.gospring.psi.GoSpringPsi.isSupportedConfigFile(file)) {
            return Result.CONTINUE;
        }
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor, psiFile ->
                psiFile instanceof org.jetbrains.yaml.psi.YAMLFile
                        ? io.infra.idea.plugin.gospring.psi.GoSpringPsi.isSupportedConfigFile(psiFile)
                        : GoSpringConfigMetadataSupport.shouldAutoPopup(psiFile, editor.getCaretModel().getOffset()));
        return Result.STOP;
    }
}
