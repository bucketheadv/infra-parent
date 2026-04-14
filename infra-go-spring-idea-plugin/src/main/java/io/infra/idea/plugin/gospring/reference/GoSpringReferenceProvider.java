package io.infra.idea.plugin.gospring.reference;

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GoSpringReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (GoSpringPsi.isGoFile(element)) {
            PsiElement stringLiteral = GoSpringPsi.findStringLiteral(element);
            if (stringLiteral == null || !stringLiteral.equals(element)) {
                return PsiReferenceBase.EMPTY_ARRAY;
            }
            List<GoSpringPsi.TagMatch> matches = GoSpringPsi.findTagMatches(stringLiteral);
            if (matches.isEmpty()) {
                return PsiReferenceBase.EMPTY_ARRAY;
            }
            List<PsiReference> references = new ArrayList<>();
            for (GoSpringPsi.TagMatch match : matches) {
                references.add(new GoSpringPsiReference(stringLiteral, match));
            }
            return references.toArray(new PsiReference[0]);
        }

        IElementType elementType = element.getNode() == null ? null : element.getNode().getElementType();
        if (elementType != PropertiesTokenTypes.KEY_CHARACTERS) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }

        String propertyKey = element.getText();
        if (propertyKey == null || propertyKey.isBlank()) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }

        List<PsiReference> references = new ArrayList<>();
        for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(element.getProject(), propertyKey)) {
            String groupPrefix = definition.getGroupPrefix();
            if (groupPrefix == null || groupPrefix.isBlank() || !propertyKey.startsWith(groupPrefix + ".")) {
                continue;
            }
            int groupLength = groupPrefix.length();
            references.add(new GoSpringConfigKeyPsiReference(
                    element,
                    new TextRange(0, groupLength),
                    propertyKey,
                    null,
                    GoSpringConfigKeyPsiReference.Kind.GROUP
            ));
            String remaining = propertyKey.substring(groupLength + 1);
            int separator = remaining.indexOf('.');
            if (separator > 0) {
                String instanceName = remaining.substring(0, separator);
                references.add(new GoSpringConfigKeyPsiReference(
                        element,
                        new TextRange(groupLength + 1, groupLength + 1 + instanceName.length()),
                        propertyKey,
                        instanceName,
                        GoSpringConfigKeyPsiReference.Kind.INSTANCE
                ));
                references.add(new GoSpringConfigKeyPsiReference(
                        element,
                        new TextRange(groupLength + 1 + instanceName.length() + 1, propertyKey.length()),
                        propertyKey,
                        instanceName,
                        GoSpringConfigKeyPsiReference.Kind.FIELD
                ));
                return references.toArray(new PsiReference[0]);
            }
        }

        references.add(new GoSpringConfigKeyPsiReference(
                element,
                new TextRange(0, propertyKey.length()),
                propertyKey,
                null,
                GoSpringConfigKeyPsiReference.Kind.FIELD
        ));
        return references.toArray(new PsiReference[0]);
    }
}
