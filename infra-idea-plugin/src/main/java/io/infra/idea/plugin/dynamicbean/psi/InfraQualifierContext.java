package io.infra.idea.plugin.dynamicbean.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * String-literal qualifier context used by custom references.
 */
public class InfraQualifierContext {
    private final PsiElement host;
    private final String beanName;
    private final String expectedTypeName;
    private final TextRange valueRange;

    public InfraQualifierContext(PsiElement host, String beanName, String expectedTypeName, TextRange valueRange) {
        this.host = host;
        this.beanName = beanName;
        this.expectedTypeName = expectedTypeName;
        this.valueRange = valueRange;
    }

    public PsiElement getHost() {
        return host;
    }

    public String getBeanName() {
        return beanName;
    }

    public String getExpectedTypeName() {
        return expectedTypeName;
    }

    public TextRange getValueRange() {
        return valueRange;
    }
}
