package io.infra.idea.plugin.dynamicbean.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.psi.InfraDynamicBeanPsi;
import io.infra.idea.plugin.dynamicbean.psi.InfraQualifierContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;

/**
 * Highlights unsupported or missing dynamic bean names used in qualifier-like annotations.
 */
public class InfraDynamicBeanInspection extends LocalInspectionTool {
    @Override
    public @NotNull String getShortName() {
        return "InfraDynamicBeanInspection";
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull com.intellij.psi.PsiElement element) {
                if (!(element instanceof PsiLiteralExpression) && !(element instanceof KtStringTemplateExpression)) {
                    return;
                }
                InfraQualifierContext context = InfraDynamicBeanPsi.getQualifierContext(element);
                if (context == null || context.getBeanName().isBlank()) {
                    return;
                }
                if (InfraDynamicBeanIndex.findByName(element.getProject(), context.getBeanName(), context.getExpectedTypeName()) == null) {
                    holder.registerProblem(element, "未找到对应的 infra 动态 Bean: " + context.getBeanName());
                }
            }
        };
    }
}
