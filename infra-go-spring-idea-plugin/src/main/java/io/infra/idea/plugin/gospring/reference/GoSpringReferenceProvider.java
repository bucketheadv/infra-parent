package io.infra.idea.plugin.gospring.reference;

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.infra.idea.plugin.gospring.index.GoSpringGormQueryIndex;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.navigation.GoSpringConfigKeyNavigationSupport;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGormQueryUsage;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.List;

public class GoSpringReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (GoSpringPsi.isGoFile(element)) {
            return buildGoReferences(element);
        }

        IElementType elementType = element.getNode() == null ? null : element.getNode().getElementType();
        if (elementType == PropertiesTokenTypes.KEY_CHARACTERS) {
            return buildPropertyKeyReferences(element);
        }
        YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);
        if (keyValue != null
                && GoSpringPsi.isSupportedConfigFile(element.getContainingFile())
                && GoSpringConfigKeyNavigationSupport.isYamlKeyElement(element, keyValue)) {
            return buildYamlKeyReferences(element, keyValue);
        }
        return PsiReferenceBase.EMPTY_ARRAY;
    }

    private PsiReference @NotNull [] buildGoReferences(@NotNull PsiElement element) {
        PsiElement stringLiteral = GoSpringPsi.findStringLiteral(element);
        if (stringLiteral == null || !stringLiteral.equals(element)) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }
        List<GoSpringPsi.TagMatch> matches = GoSpringPsi.findTagMatches(stringLiteral);
        List<PsiReference> references = new ArrayList<>();
        for (GoSpringPsi.TagMatch match : matches) {
            references.add(new GoSpringPsiReference(stringLiteral, match));
        }
        for (GoSpringGormQueryUsage usage : GoSpringGormQueryIndex.findUsagesInLiteral(stringLiteral)) {
            references.add(new GoSpringGormSqlPsiReference(stringLiteral, usage));
        }
        if (references.isEmpty()) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }
        return references.toArray(new PsiReference[0]);
    }

    private PsiReference @NotNull [] buildPropertyKeyReferences(@NotNull PsiElement element) {
        String propertyKey = element.getText();
        if (propertyKey == null || propertyKey.isBlank()) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }

        List<PsiReference> references = new ArrayList<>();
        for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(element.getProject(), propertyKey)) {
            String groupPrefix = definition.getGroupPrefix();
            if (groupPrefix == null || groupPrefix.isBlank()) {
                continue;
            }
            int groupLength = groupPrefix.length();
            if (propertyKey.equals(groupPrefix)) {
                references.add(new GoSpringConfigKeyPsiReference(
                        element,
                        new TextRange(0, groupLength),
                        propertyKey,
                        null,
                        GoSpringConfigKeyPsiReference.Kind.GROUP,
                        0
                ));
                return references.toArray(new PsiReference[0]);
            }
            if (!propertyKey.startsWith(groupPrefix + ".")) {
                continue;
            }
            references.add(new GoSpringConfigKeyPsiReference(
                    element,
                    new TextRange(0, groupLength),
                    propertyKey,
                    null,
                    GoSpringConfigKeyPsiReference.Kind.GROUP,
                    0
            ));
            String remaining = propertyKey.substring(groupLength + 1);
            int separator = remaining.indexOf('.');
            if (separator < 0) {
                references.add(new GoSpringConfigKeyPsiReference(
                        element,
                        new TextRange(groupLength + 1, propertyKey.length()),
                        propertyKey,
                        remaining,
                        GoSpringConfigKeyPsiReference.Kind.INSTANCE,
                        groupLength + 1
                ));
                return references.toArray(new PsiReference[0]);
            }
            if (separator > 0) {
                String instanceName = remaining.substring(0, separator);
                references.add(new GoSpringConfigKeyPsiReference(
                        element,
                        new TextRange(groupLength + 1, groupLength + 1 + instanceName.length()),
                        propertyKey,
                        instanceName,
                        GoSpringConfigKeyPsiReference.Kind.INSTANCE,
                        groupLength + 1
                ));
                references.add(new GoSpringConfigKeyPsiReference(
                        element,
                        new TextRange(groupLength + 1 + instanceName.length() + 1, propertyKey.length()),
                        propertyKey,
                        instanceName,
                        GoSpringConfigKeyPsiReference.Kind.FIELD,
                        groupLength + 1 + instanceName.length() + 1
                ));
                return references.toArray(new PsiReference[0]);
            }
        }

        references.add(new GoSpringConfigKeyPsiReference(
                element,
                new TextRange(0, propertyKey.length()),
                propertyKey,
                null,
                GoSpringConfigKeyPsiReference.Kind.FIELD,
                0
        ));
        return references.toArray(new PsiReference[0]);
    }

    private PsiReference @NotNull [] buildYamlKeyReferences(@NotNull PsiElement element, @NotNull YAMLKeyValue keyValue) {
        String propertyKey = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(keyValue);
        String keyText = keyValue.getKeyText();
        if (propertyKey == null || propertyKey.isBlank() || keyText == null || keyText.isBlank()) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }
        int offsetInKey = GoSpringConfigKeyNavigationSupport.getYamlOffsetInFullKey(keyValue, 0);
        GoSpringConfigKeyNavigationSupport.ResolvedSegment segment =
                GoSpringConfigKeyNavigationSupport.resolveSegment(element.getProject(), propertyKey, offsetInKey);
        if (segment == null) {
            return PsiReferenceBase.EMPTY_ARRAY;
        }
        return new PsiReference[]{
                new GoSpringConfigKeyPsiReference(
                        element,
                        new TextRange(0, element.getTextLength()),
                        propertyKey,
                        segment.getInstanceName(),
                        segment.getKind(),
                        offsetInKey
                )
        };
    }
}
