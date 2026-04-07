package io.infra.idea.plugin.dynamicbean.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import io.infra.idea.plugin.dynamicbean.psi.InfraDynamicBeanPsi;
import io.infra.idea.plugin.dynamicbean.psi.InfraQualifierContext;
import org.jetbrains.annotations.NotNull;

/**
 * Converts supported qualifier string literals into references backed by infra properties.
 */
public class InfraDynamicBeanReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        InfraQualifierContext qualifierContext = InfraDynamicBeanPsi.getQualifierContext(element);
        if (qualifierContext == null) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }
        return new PsiReference[]{new InfraDynamicBeanPsiReference(qualifierContext)};
    }
}
