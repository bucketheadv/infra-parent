package io.infra.idea.plugin.gospring.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.infra.idea.plugin.gospring.navigation.InfraGoApplogYamlNavigation;
import org.jetbrains.annotations.NotNull;

/**
 * {@code applog} 包内命名 logger 常量（如 {@code NameApp}）→ {@code loggers.<键>} YAML。
 */
public class ApplogLoggerConstYamlReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private final String loggerYamlKey;

    public ApplogLoggerConstYamlReference(@NotNull PsiElement identifierHost, @NotNull String loggerYamlKey) {
        super(identifierHost, TextRange.from(0, identifierHost.getTextLength()), false);
        this.loggerYamlKey = loggerYamlKey;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        PsiElement[] targets = InfraGoApplogYamlNavigation.findYamlTargetsForNamedLoggerConst(
                getElement().getProject(),
                loggerYamlKey
        );
        if (targets == null || targets.length == 0) {
            return ResolveResult.EMPTY_ARRAY;
        }
        ResolveResult[] results = new ResolveResult[targets.length];
        for (int i = 0; i < targets.length; i++) {
            results[i] = new PsiElementResolveResult(targets[i]);
        }
        return results;
    }
}
