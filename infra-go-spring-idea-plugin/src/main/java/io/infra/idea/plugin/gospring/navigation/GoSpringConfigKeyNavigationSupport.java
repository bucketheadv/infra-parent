package io.infra.idea.plugin.gospring.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
import io.infra.idea.plugin.gospring.reference.GoSpringConfigKeyPsiReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

public final class GoSpringConfigKeyNavigationSupport {
    private GoSpringConfigKeyNavigationSupport() {
    }

    public static @Nullable ResolvedSegment resolveSegment(Project project, String propertyKey, int offsetInKey) {
        if (propertyKey == null || propertyKey.isBlank() || offsetInKey < 0) {
            return null;
        }
        for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(project, propertyKey)) {
            String prefix = definition.getGroupPrefix();
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (propertyKey.equals(prefix)) {
                if (offsetInKey < prefix.length()) {
                    return new ResolvedSegment(GoSpringConfigKeyPsiReference.Kind.GROUP, null);
                }
                continue;
            }
            if (!propertyKey.startsWith(prefix + ".")) {
                continue;
            }
            if (offsetInKey < prefix.length()) {
                return new ResolvedSegment(GoSpringConfigKeyPsiReference.Kind.GROUP, null);
            }
            String remaining = propertyKey.substring(prefix.length() + 1);
            int separator = remaining.indexOf('.');
            if (separator < 0) {
                int instanceStart = prefix.length() + 1;
                if (offsetInKey >= instanceStart && offsetInKey < propertyKey.length()) {
                    return new ResolvedSegment(GoSpringConfigKeyPsiReference.Kind.INSTANCE, remaining);
                }
                continue;
            }
            String instanceName = remaining.substring(0, separator);
            int instanceStart = prefix.length() + 1;
            int instanceEnd = instanceStart + instanceName.length();
            if (offsetInKey >= instanceStart && offsetInKey < instanceEnd) {
                return new ResolvedSegment(GoSpringConfigKeyPsiReference.Kind.INSTANCE, instanceName);
            }
            int fieldStart = instanceEnd + 1;
            if (offsetInKey >= fieldStart && offsetInKey < propertyKey.length()) {
                return new ResolvedSegment(GoSpringConfigKeyPsiReference.Kind.FIELD, instanceName);
            }
        }
        return new ResolvedSegment(GoSpringConfigKeyPsiReference.Kind.FIELD, null);
    }

    public static PsiElement @Nullable [] findTargets(Project project, String propertyKey, int offsetInKey) {
        ResolvedSegment segment = resolveSegment(project, propertyKey, offsetInKey);
        if (segment == null) {
            return null;
        }
        Set<PsiElement> targets = new LinkedHashSet<>();
        if (segment.getKind() == GoSpringConfigKeyPsiReference.Kind.GROUP) {
            for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(project, propertyKey)) {
                if (definition.getPsiElement() != null) {
                    targets.add(definition.getPsiElement());
                }
            }
            return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
        }
        if (segment.getKind() == GoSpringConfigKeyPsiReference.Kind.INSTANCE) {
            for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(project, propertyKey)) {
                targets.addAll(GoSpringIndex.findAutowireUsages(project, definition, segment.getInstanceName()));
            }
            return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
        }
        targets.addAll(findScopedFieldUsageTargets(project, propertyKey));
        if (targets.isEmpty()) {
            targets.addAll(GoSpringIndex.findValueUsages(project, propertyKey));
        }
        for (GoSpringExternalConfigDefinition definition : GoSpringIndex.findExternalConfigDefinitions(project, propertyKey)) {
            if (definition.getPsiElement() != null) {
                targets.add(definition.getPsiElement());
            }
        }
        return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
    }

    public static TextRange resolveFieldRange(Project project, String propertyKey, int keyStartOffset) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return TextRange.from(keyStartOffset, 0);
        }
        for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(project, propertyKey)) {
            String prefix = definition.getGroupPrefix();
            if (prefix == null || prefix.isBlank() || !propertyKey.startsWith(prefix + ".")) {
                continue;
            }
            String remaining = propertyKey.substring(prefix.length() + 1);
            int separator = remaining.indexOf('.');
            if (separator < 0 || separator == remaining.length() - 1) {
                break;
            }
            int fieldStart = prefix.length() + 1 + separator + 1;
            return TextRange.from(keyStartOffset + fieldStart, propertyKey.length() - fieldStart);
        }
        return TextRange.from(keyStartOffset, propertyKey.length());
    }

    public static PsiElement @Nullable [] findPreferredTargetsForGutter(Project project, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return null;
        }
        PsiElement[] fieldTargets = findFieldTargets(project, propertyKey);
        if (fieldTargets != null && fieldTargets.length > 0) {
            return fieldTargets;
        }
        PsiElement[] instanceTargets = findInstanceTargets(project, propertyKey);
        if (instanceTargets != null && instanceTargets.length > 0) {
            return instanceTargets;
        }
        return findGroupTargets(project, propertyKey);
    }

    private static PsiElement @Nullable [] findFieldTargets(Project project, String propertyKey) {
        Set<PsiElement> targets = new LinkedHashSet<>();
        targets.addAll(findScopedFieldUsageTargets(project, propertyKey));
        if (targets.isEmpty()) {
            targets.addAll(GoSpringIndex.findValueUsages(project, propertyKey));
        }
        for (GoSpringExternalConfigDefinition definition : GoSpringIndex.findExternalConfigDefinitions(project, propertyKey)) {
            if (definition.getPsiElement() != null) {
                targets.add(definition.getPsiElement());
            }
        }
        return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
    }

    private static Set<PsiElement> findScopedFieldUsageTargets(Project project, String propertyKey) {
        Set<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(project, propertyKey)) {
            for (String providedType : definition.getProvidedTypes()) {
                targets.addAll(GoSpringIndex.findValueUsages(project, propertyKey, providedType));
            }
        }
        return targets;
    }

    private static PsiElement @Nullable [] findInstanceTargets(Project project, String propertyKey) {
        Set<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(project, propertyKey)) {
            String instanceName = resolveInstanceName(definition, propertyKey);
            if (instanceName == null) {
                continue;
            }
            targets.addAll(GoSpringIndex.findAutowireUsages(project, definition, instanceName));
        }
        return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
    }

    private static PsiElement @Nullable [] findGroupTargets(Project project, String propertyKey) {
        Set<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringGroupDefinition definition : GoSpringIndex.findGroupDefinitions(project, propertyKey)) {
            if (definition.getPsiElement() != null) {
                targets.add(definition.getPsiElement());
            }
        }
        return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
    }

    private static @Nullable String resolveInstanceName(GoSpringGroupDefinition definition, String propertyKey) {
        String prefix = definition.getGroupPrefix();
        if (prefix == null || prefix.isBlank() || !propertyKey.startsWith(prefix + ".")) {
            return null;
        }
        String remaining = propertyKey.substring(prefix.length() + 1);
        int separator = remaining.indexOf('.');
        if (separator < 0) {
            return remaining.isBlank() ? null : remaining;
        }
        return separator == 0 ? null : remaining.substring(0, separator);
    }

    public static @Nullable String buildYamlPropertyKey(YAMLKeyValue keyValue) {
        LinkedList<String> segments = new LinkedList<>();
        YAMLKeyValue cursor = keyValue;
        while (cursor != null) {
            String segment = cursor.getKeyText();
            if (segment == null || segment.isBlank()) {
                return null;
            }
            segments.addFirst(segment);
            cursor = findParentYamlKeyValue(cursor);
        }
        return String.join(".", segments);
    }

    public static boolean isYamlKeyElement(PsiElement element, YAMLKeyValue keyValue) {
        String keyText = keyValue.getKeyText();
        if (keyText == null || keyText.isBlank()) {
            return false;
        }
        return element.getTextRange().getStartOffset() == keyValue.getTextRange().getStartOffset();
    }

    public static int getYamlOffsetInFullKey(YAMLKeyValue keyValue, int offsetInSegment) {
        String keyText = keyValue.getKeyText();
        String propertyKey = buildYamlPropertyKey(keyValue);
        if (keyText == null || propertyKey == null) {
            return -1;
        }
        return propertyKey.length() - keyText.length() + Math.max(0, offsetInSegment);
    }

    private static @Nullable YAMLKeyValue findParentYamlKeyValue(@Nullable PsiElement element) {
        PsiElement cursor = element == null ? null : element.getParent();
        while (cursor != null && !(cursor instanceof YAMLFile)) {
            if (cursor instanceof YAMLKeyValue keyValue) {
                return keyValue;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    public static final class ResolvedSegment {
        private final GoSpringConfigKeyPsiReference.Kind kind;
        private final String instanceName;

        private ResolvedSegment(GoSpringConfigKeyPsiReference.Kind kind, @Nullable String instanceName) {
            this.kind = kind;
            this.instanceName = instanceName;
        }

        public GoSpringConfigKeyPsiReference.Kind getKind() {
            return kind;
        }

        public @Nullable String getInstanceName() {
            return instanceName;
        }
    }
}
