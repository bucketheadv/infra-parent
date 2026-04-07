package io.infra.idea.plugin.dynamicbean.psi;

import com.intellij.psi.PsiElement;

/**
 * Bean injection site abstraction used by navigation and inspection logic.
 */
public class InfraInjectionPoint {
    private final PsiElement anchor;
    private final String typeName;
    private final String qualifierValue;

    public InfraInjectionPoint(PsiElement anchor, String typeName, String qualifierValue) {
        this.anchor = anchor;
        this.typeName = typeName;
        this.qualifierValue = qualifierValue;
    }

    public PsiElement getAnchor() {
        return anchor;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getQualifierValue() {
        return qualifierValue;
    }
}
