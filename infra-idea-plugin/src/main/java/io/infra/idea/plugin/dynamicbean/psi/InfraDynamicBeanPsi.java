package io.infra.idea.plugin.dynamicbean.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtModifierListOwner;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;

import java.util.List;
import java.util.Set;

/**
 * PSI utilities shared by reference, navigation and inspection implementations.
 */
public final class InfraDynamicBeanPsi {
    private static final Set<String> QUALIFIER_ANNOTATIONS = Set.of(
            "org.springframework.beans.factory.annotation.Qualifier",
            "javax.inject.Named",
            "jakarta.inject.Named"
    );

    private static final Set<String> RESOURCE_ANNOTATIONS = Set.of(
            "javax.annotation.Resource",
            "jakarta.annotation.Resource"
    );

    private InfraDynamicBeanPsi() {
    }

    public static @Nullable InfraQualifierContext getQualifierContext(PsiElement element) {
        if (element instanceof PsiLiteralExpression literal && literal.getValue() instanceof String value) {
            PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
            if (annotation == null || !isSupportedJavaQualifierAnnotation(annotation)) {
                return null;
            }
            PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiField.class, PsiParameter.class);
            if (owner == null) {
                return null;
            }
            return new InfraQualifierContext(element, value, getJavaTypeName(owner), TextRange.from(1, value.length()));
        }
        if (element instanceof KtStringTemplateExpression stringExpression) {
            String value = getKotlinStringValue(stringExpression);
            if (value == null) {
                return null;
            }
            KtAnnotationEntry annotationEntry = PsiTreeUtil.getParentOfType(element, KtAnnotationEntry.class);
            if (annotationEntry == null || !isSupportedKotlinQualifierAnnotation(annotationEntry)) {
                return null;
            }
            KtModifierListOwner owner = PsiTreeUtil.getParentOfType(annotationEntry, KtParameter.class, KtProperty.class);
            if (owner == null) {
                return null;
            }
            return new InfraQualifierContext(element, value, getKotlinTypeName(owner), TextRange.from(1, value.length()));
        }
        return null;
    }

    public static @Nullable InfraInjectionPoint getInjectionPoint(PsiElement element) {
        PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
        if (psiField != null && psiField.getNameIdentifier() == element) {
            return new InfraInjectionPoint(element, psiField.getType().getCanonicalText(), readJavaQualifierValue(psiField));
        }

        PsiParameter psiParameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
        if (psiParameter != null && psiParameter.getNameIdentifier() == element) {
            return new InfraInjectionPoint(element, psiParameter.getType().getCanonicalText(), readJavaQualifierValue(psiParameter));
        }

        KtParameter ktParameter = PsiTreeUtil.getParentOfType(element, KtParameter.class, false);
        if (ktParameter != null && ktParameter.getNameIdentifier() == element) {
            return new InfraInjectionPoint(element, getKotlinTypeName(ktParameter), readKotlinQualifierValue(ktParameter));
        }

        KtProperty ktProperty = PsiTreeUtil.getParentOfType(element, KtProperty.class, false);
        if (ktProperty != null && ktProperty.getNameIdentifier() == element) {
            return new InfraInjectionPoint(element, getKotlinTypeName(ktProperty), readKotlinQualifierValue(ktProperty));
        }
        return null;
    }

    private static boolean isSupportedJavaQualifierAnnotation(PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        return QUALIFIER_ANNOTATIONS.contains(qualifiedName) || RESOURCE_ANNOTATIONS.contains(qualifiedName);
    }

    private static boolean isSupportedKotlinQualifierAnnotation(KtAnnotationEntry annotationEntry) {
        String shortName = annotationEntry.getShortName() == null ? null : annotationEntry.getShortName().asString();
        return "Qualifier".equals(shortName) || "Named".equals(shortName) || "Resource".equals(shortName);
    }

    private static @Nullable String getJavaTypeName(PsiModifierListOwner owner) {
        if (owner instanceof PsiField field) {
            return field.getType().getCanonicalText();
        }
        if (owner instanceof PsiParameter parameter) {
            return parameter.getType().getCanonicalText();
        }
        return null;
    }

    private static @Nullable String getKotlinTypeName(KtModifierListOwner owner) {
        if (owner instanceof KtParameter parameter && parameter.getTypeReference() != null) {
            return parameter.getTypeReference().getText();
        }
        if (owner instanceof KtProperty property && property.getTypeReference() != null) {
            return property.getTypeReference().getText();
        }
        return null;
    }

    private static @Nullable String readJavaQualifierValue(PsiModifierListOwner owner) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (QUALIFIER_ANNOTATIONS.contains(qualifiedName)) {
                return readJavaAnnotationValue(annotation, "value");
            }
            if (RESOURCE_ANNOTATIONS.contains(qualifiedName)) {
                String resourceName = readJavaAnnotationValue(annotation, "name");
                if (resourceName != null && !resourceName.isBlank()) {
                    return resourceName;
                }
                return getJavaInjectionName(owner);
            }
        }
        return null;
    }

    private static @Nullable String readJavaAnnotationValue(PsiAnnotation annotation, String attributeName) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value == null && "value".equals(attributeName)) {
            value = annotation.findDeclaredAttributeValue("value");
        }
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }

    private static @Nullable String readKotlinQualifierValue(KtModifierListOwner owner) {
        List<KtAnnotationEntry> annotations = owner.getAnnotationEntries();
        for (KtAnnotationEntry annotation : annotations) {
            String shortName = annotation.getShortName() == null ? null : annotation.getShortName().asString();
            if ("Qualifier".equals(shortName) || "Named".equals(shortName)) {
                return readKotlinAnnotationValue(annotation, null);
            }
            if ("Resource".equals(shortName)) {
                String resourceName = readKotlinAnnotationValue(annotation, "name");
                if (resourceName != null && !resourceName.isBlank()) {
                    return resourceName;
                }
                return getKotlinInjectionName(owner);
            }
        }
        return null;
    }

    private static @Nullable String getJavaInjectionName(PsiModifierListOwner owner) {
        if (owner instanceof PsiField field) {
            return field.getName();
        }
        if (owner instanceof PsiParameter parameter) {
            return parameter.getName();
        }
        return null;
    }

    private static @Nullable String getKotlinInjectionName(KtModifierListOwner owner) {
        if (owner instanceof KtParameter parameter) {
            return parameter.getName();
        }
        if (owner instanceof KtProperty property) {
            return property.getName();
        }
        return null;
    }

    private static @Nullable String readKotlinAnnotationValue(KtAnnotationEntry annotation, @Nullable String argumentName) {
        if (annotation.getValueArguments().isEmpty()) {
            return null;
        }
        for (ValueArgument argument : annotation.getValueArguments()) {
            String currentName = argument.getArgumentName() == null
                    ? null
                    : argument.getArgumentName().getAsName().asString();
            if (argumentName == null || argumentName.equals(currentName) || currentName == null) {
                if (argument.getArgumentExpression() instanceof KtStringTemplateExpression stringExpression) {
                    String value = getKotlinStringValue(stringExpression);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable String getKotlinStringValue(KtStringTemplateExpression expression) {
        String text = expression.getText();
        if (text.length() < 2 || text.contains("$")) {
            return null;
        }
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return null;
    }
}
