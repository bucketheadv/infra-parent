package io.infra.idea.plugin.gospring.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection entry for malformed GORM struct tags.
 */
public class GoSpringGormTagInspection extends LocalInspectionTool {
    @Override
    public @NotNull String getDisplayName() {
        return "GORM tag validation";
    }

    @Override
    public @NotNull String getGroupDisplayName() {
        return "Infra Go-Spring";
    }

    @Override
    public @NotNull String getShortName() {
        return "GoSpringGormTag";
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                for (GoSpringGormTagSupport.Issue issue : GoSpringGormTagSupport.validate(element)) {
                    holder.registerProblem(
                            element,
                            issue.getMessage(),
                            ProblemHighlightType.GENERIC_ERROR,
                            issue.getRangeInElement()
                    );
                }
            }
        };
    }
}
