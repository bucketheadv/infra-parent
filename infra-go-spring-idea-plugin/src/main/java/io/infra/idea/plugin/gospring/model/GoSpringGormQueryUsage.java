package io.infra.idea.plugin.gospring.model;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * A SQL field usage found inside a GORM query string.
 */
public class GoSpringGormQueryUsage {
    private final String methodName;
    private final String structName;
    private final String columnName;
    private final String fieldName;
    private final PsiElement stringLiteral;
    private final TextRange rangeInElement;

    public GoSpringGormQueryUsage(String methodName,
                                  String structName,
                                  String columnName,
                                  String fieldName,
                                  PsiElement stringLiteral,
                                  TextRange rangeInElement) {
        this.methodName = methodName;
        this.structName = structName;
        this.columnName = columnName;
        this.fieldName = fieldName;
        this.stringLiteral = stringLiteral;
        this.rangeInElement = rangeInElement;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getStructName() {
        return structName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public PsiElement getStringLiteral() {
        return stringLiteral;
    }

    public TextRange getRangeInElement() {
        return rangeInElement;
    }
}
