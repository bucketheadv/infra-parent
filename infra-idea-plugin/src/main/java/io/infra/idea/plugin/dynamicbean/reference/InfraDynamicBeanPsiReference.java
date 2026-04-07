package io.infra.idea.plugin.dynamicbean.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanConfigProperty;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import io.infra.idea.plugin.dynamicbean.psi.InfraQualifierContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference from annotation bean names to their defining properties.
 */
public class InfraDynamicBeanPsiReference extends PsiReferenceBase<PsiElement> {
    private final InfraQualifierContext qualifierContext;

    protected InfraDynamicBeanPsiReference(InfraQualifierContext qualifierContext) {
        super(qualifierContext.getHost(), qualifierContext.getValueRange(), true);
        this.qualifierContext = qualifierContext;
    }

    @Override
    public @Nullable PsiElement resolve() {
        InfraDynamicBeanDefinition definition = InfraDynamicBeanIndex.findByName(
                myElement.getProject(),
                qualifierContext.getBeanName(),
                qualifierContext.getExpectedTypeName()
        );
        return definition == null ? null : definition.getNavigationProperty().getPsiElement();
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<Object> variants = new ArrayList<>();
        for (InfraDynamicBeanDefinition definition : InfraDynamicBeanIndex.getBeans(myElement.getProject(), qualifierContext.getExpectedTypeName())) {
            InfraDynamicBeanConfigProperty property = definition.getNavigationProperty();
            variants.add(LookupElementBuilder.create(definition.getBeanName())
                    .withTypeText(property.getKey(), true));
        }
        return variants.toArray();
    }
}
