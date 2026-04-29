package io.infra.idea.plugin.gospring.model;

import com.intellij.psi.PsiElement;

/**
 * GORM model field definition mapped from a Go struct field.
 */
public class GoSpringGormFieldDefinition {
    private final String structName;
    private final String fieldName;
    private final String columnName;
    private final PsiElement psiElement;

    public GoSpringGormFieldDefinition(String structName, String fieldName, String columnName, PsiElement psiElement) {
        this.structName = structName;
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.psiElement = psiElement;
    }

    public String getStructName() {
        return structName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getColumnName() {
        return columnName;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }
}
