package io.infra.idea.plugin.gospring.metadata;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.model.GoSpringConfigMetadata;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public final class GoSpringConfigMetadataSupport {
    private GoSpringConfigMetadataSupport() {
    }

    public static @Nullable GoSpringConfigMetadata resolveMetadata(PsiElement element) {
        String key = resolveConfigKey(element);
        if (key == null) {
            return null;
        }
        return GoSpringIndex.findConfigMetadata(element.getProject(), key);
    }

    public static @Nullable PsiElement findDocumentationTarget(@Nullable Editor editor,
                                                               @Nullable PsiFile file,
                                                               @Nullable PsiElement contextElement,
                                                               int targetOffset) {
        if (file == null || !GoSpringPsi.isSupportedConfigFile(file)) {
            return null;
        }
        PsiElement target = file.findElementAt(targetOffset);
        if (target == null) {
            target = contextElement;
        }
        if (target == null) {
            return null;
        }
        return resolveConfigKey(target) == null ? null : target;
    }

    public static boolean shouldAutoPopup(@Nullable PsiFile file, int offset) {
        if (file == null || !GoSpringPsi.isSupportedConfigFile(file)) {
            return false;
        }
        if (file instanceof com.intellij.lang.properties.psi.PropertiesFile) {
            return extractPropertiesKeyPrefix(file.getText(), offset) != null;
        }
        if (file instanceof YAMLFile) {
            return extractYamlCompletionContext(file, file.getText(), offset) != null;
        }
        return false;
    }

    public static Map<String, GoSpringConfigMetadata> collectPropertiesMetadata(Project project,
                                                                                @Nullable PsiFile file,
                                                                                @Nullable String prefix) {
        Map<String, GoSpringConfigMetadata> result = new LinkedHashMap<>();
        String normalizedPrefix = prefix == null ? "" : prefix.trim();
        Set<String> existingKeys = collectExistingConfigKeys(file);
        for (GoSpringConfigMetadata metadata : GoSpringIndex.getConfigMetadata(project)) {
            String suggestion = resolvePropertySuggestionKey(metadata.getKey(), normalizedPrefix);
            if (suggestion == null) {
                continue;
            }
            if (existingKeys.contains(suggestion)) {
                continue;
            }
            result.putIfAbsent(suggestion, metadata);
        }
        return result;
    }

    public static Map<String, GoSpringConfigMetadata> collectYamlMetadata(Project project,
                                                                          @Nullable PsiFile file,
                                                                          @Nullable String parentKey,
                                                                          @Nullable String segmentPrefix) {
        Map<String, GoSpringConfigMetadata> result = new LinkedHashMap<>();
        String normalizedParent = parentKey == null ? "" : parentKey.trim();
        String normalizedSegment = segmentPrefix == null ? "" : segmentPrefix.trim();
        Set<String> existingChildren = collectExistingYamlChildren(file, normalizedParent);
        for (GoSpringConfigMetadata metadata : GoSpringIndex.getConfigMetadata(project)) {
            String suggestion = resolveYamlSuggestion(metadata.getKey(), normalizedParent);
            if (suggestion == null || suggestion.isBlank()) {
                continue;
            }
            if (!normalizedSegment.isBlank() && !suggestion.startsWith(normalizedSegment)) {
                continue;
            }
            if (existingChildren.contains(suggestion)) {
                continue;
            }
            result.putIfAbsent(suggestion, metadata);
        }
        return result;
    }

    private static @Nullable String resolvePropertySuggestionKey(String metadataKey, String typedPrefix) {
        String normalizedPrefix = typedPrefix == null ? "" : typedPrefix.trim();
        if (normalizedPrefix.isBlank()) {
            return metadataKey;
        }
        if (metadataKey.startsWith(normalizedPrefix)) {
            return metadataKey;
        }
        int wildcardIndex = metadataKey.indexOf(".*.");
        if (wildcardIndex < 0) {
            return null;
        }
        String groupPrefix = metadataKey.substring(0, wildcardIndex);
        String suffix = metadataKey.substring(wildcardIndex + 3);
        String expectedPrefix = groupPrefix + ".";
        if (!normalizedPrefix.startsWith(expectedPrefix)) {
            return null;
        }
        String remaining = normalizedPrefix.substring(expectedPrefix.length());
        int nextDot = remaining.indexOf('.');
        if (nextDot <= 0) {
            return null;
        }
        String instanceName = remaining.substring(0, nextDot);
        String concreteKey = groupPrefix + "." + instanceName + "." + suffix;
        return concreteKey.startsWith(normalizedPrefix) ? concreteKey : null;
    }

    private static @Nullable String resolveYamlSuggestion(String metadataKey, String parentKey) {
        String normalizedParent = parentKey == null ? "" : parentKey.trim();
        String remaining = metadataKey;
        if (normalizedParent.isBlank()) {
            int separator = remaining.indexOf('.');
            return separator >= 0 ? remaining.substring(0, separator) : remaining;
        }
        String expectedPrefix = normalizedParent + ".";
        if (metadataKey.startsWith(expectedPrefix)) {
            remaining = metadataKey.substring(expectedPrefix.length());
            int separator = remaining.indexOf('.');
            return separator >= 0 ? remaining.substring(0, separator) : remaining;
        }
        int wildcardIndex = metadataKey.indexOf(".*.");
        if (wildcardIndex < 0) {
            return null;
        }
        String groupPrefix = metadataKey.substring(0, wildcardIndex);
        String suffix = metadataKey.substring(wildcardIndex + 3);
        String groupExpectedPrefix = groupPrefix + ".";
        if (!normalizedParent.startsWith(groupExpectedPrefix)) {
            return null;
        }
        String instancePart = normalizedParent.substring(groupExpectedPrefix.length());
        if (instancePart.isBlank() || instancePart.contains(".")) {
            return null;
        }
        int separator = suffix.indexOf('.');
        return separator >= 0 ? suffix.substring(0, separator) : suffix;
    }

    public static @Nullable String extractPropertiesKeyPrefix(PsiFile file, int offset) {
        return extractPropertiesKeyPrefix(file.getText(), offset);
    }

    public static @Nullable String extractPropertiesKeyPrefix(String text, int offset) {
        String linePrefix = readCurrentLinePrefix(text, offset);
        if (linePrefix == null) {
            return null;
        }
        String trimmed = stripLeadingWhitespace(linePrefix);
        int separator = findFirstSeparator(trimmed);
        if (separator >= 0) {
            trimmed = trimmed.substring(0, separator);
        }
        trimmed = trimmed.replace("IntellijIdeaRulezzz", "").trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    public static @Nullable YamlCompletionContext extractYamlCompletionContext(PsiFile file, int offset) {
        return extractYamlCompletionContext(file, file.getText(), offset);
    }

    public static @Nullable YamlCompletionContext extractYamlCompletionContext(PsiFile file, String text, int offset) {
        PsiElement element = file.findElementAt(Math.max(0, Math.min(offset, file.getTextLength() == 0 ? 0 : file.getTextLength() - 1)));
        YAMLKeyValue keyValue = element == null ? null : PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);
        if (keyValue != null) {
            String keyText = keyValue.getKeyText();
            int keyStart = keyValue.getTextRange().getStartOffset();
            if (keyText != null && offset >= keyStart && offset <= keyStart + keyText.length()) {
                int relativeOffset = Math.max(0, Math.min(offset - keyStart, keyText.length()));
                String typedPrefix = keyText.substring(0, relativeOffset).replace("IntellijIdeaRulezzz", "");
                if (typedPrefix.contains(".")) {
                    return new YamlCompletionContext(null, typedPrefix, true);
                }
                return new YamlCompletionContext(buildYamlParentKey(keyValue), typedPrefix, false);
            }
        }

        String linePrefix = readCurrentLinePrefix(text, offset);
        if (linePrefix == null) {
            return null;
        }
        String trimmed = stripLeadingWhitespace(linePrefix).replace("IntellijIdeaRulezzz", "");
        if (trimmed.contains(":")) {
            return null;
        }
        if (trimmed.contains(".")) {
            return new YamlCompletionContext(null, trimmed, true);
        }
        int indent = linePrefix.length() - stripLeadingWhitespace(linePrefix).length();
        String parentKey = findYamlParentKeyForIndent((YAMLFile) file, offset, indent);
        return new YamlCompletionContext(parentKey, trimmed, false);
    }

    public static @Nullable String resolveConfigKey(PsiElement element) {
        Property property = PsiTreeUtil.getParentOfType(element, Property.class, false);
        if (property != null) {
            String key = property.getKey();
            return key == null || key.isBlank() ? null : key;
        }

        YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, false);
        if (keyValue == null) {
            return null;
        }
        return buildYamlPropertyKey(keyValue);
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

    public static @Nullable String buildYamlParentKey(YAMLKeyValue keyValue) {
        YAMLKeyValue parent = findParentYamlKeyValue(keyValue);
        return parent == null ? null : buildYamlPropertyKey(parent);
    }

    public static String buildDocumentation(GoSpringConfigMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("<div class='definition'><pre>")
                .append(StringUtil.escapeXmlEntities(metadata.getKey()))
                .append("</pre></div>");
        builder.append("<div class='content'>Go-Spring configuration property</div>");
        builder.append("<table>");
        appendRow(builder, "Type", metadata.getTypeName());
        appendRow(builder, "Default", metadata.getDefaultValue());
        appendRow(builder, "Source", metadata.getSourceLabel());
        if (metadata.getPsiElement() != null && metadata.getPsiElement().getContainingFile() != null) {
            appendRow(builder, "Declared In", metadata.getPsiElement().getContainingFile().getName());
        }
        builder.append("</table>");
        return builder.toString();
    }

    private static void appendRow(StringBuilder builder, String label, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append("<tr><td><b>")
                .append(StringUtil.escapeXmlEntities(label))
                .append(":</b></td><td>")
                .append(StringUtil.escapeXmlEntities(value))
                .append("</td></tr>");
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

    private static @Nullable String readCurrentLinePrefix(PsiFile file, int offset) {
        return readCurrentLinePrefix(file.getText(), offset);
    }

    private static @Nullable String readCurrentLinePrefix(String text, int offset) {
        if (text == null) {
            return null;
        }
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        int lineStart = text.lastIndexOf('\n', Math.max(0, safeOffset - 1));
        return text.substring(lineStart + 1, safeOffset);
    }

    private static Set<String> collectExistingConfigKeys(@Nullable PsiFile file) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (file instanceof PropertiesFile propertiesFile) {
            Collection<IProperty> properties = propertiesFile.getProperties();
            for (IProperty property : properties) {
                String key = property.getKey();
                if (key != null && !key.isBlank()) {
                    result.add(key);
                }
            }
            return result;
        }
        if (file instanceof YAMLFile yamlFile) {
            for (YAMLDocument document : yamlFile.getDocuments()) {
                collectYamlKeys(document.getTopLevelValue(), result);
            }
        }
        return result;
    }

    private static void collectYamlKeys(@Nullable PsiElement element, Set<String> result) {
        if (!(element instanceof YAMLMapping mapping)) {
            return;
        }
        for (YAMLKeyValue keyValue : mapping.getKeyValues()) {
            String key = buildYamlPropertyKey(keyValue);
            if (key != null && !key.isBlank()) {
                result.add(key);
            }
            collectYamlKeys(keyValue.getValue(), result);
        }
    }

    private static Set<String> collectExistingYamlChildren(@Nullable PsiFile file, @Nullable String parentKey) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!(file instanceof YAMLFile)) {
            return result;
        }
        String normalizedParent = parentKey == null ? "" : parentKey.trim();
        for (String key : collectExistingConfigKeys(file)) {
            String remaining = key;
            if (!normalizedParent.isBlank()) {
                String expectedPrefix = normalizedParent + ".";
                if (!key.startsWith(expectedPrefix)) {
                    continue;
                }
                remaining = key.substring(expectedPrefix.length());
            }
            int separator = remaining.indexOf('.');
            String suggestion = separator >= 0 ? remaining.substring(0, separator) : remaining;
            if (!suggestion.isBlank()) {
                result.add(suggestion);
            }
        }
        return result;
    }

    private static int findFirstSeparator(String text) {
        int equalsIndex = text.indexOf('=');
        int colonIndex = text.indexOf(':');
        if (equalsIndex < 0) {
            return colonIndex;
        }
        if (colonIndex < 0) {
            return equalsIndex;
        }
        return Math.min(equalsIndex, colonIndex);
    }

    private static String stripLeadingWhitespace(String text) {
        int index = 0;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return text.substring(index);
    }

    private static @Nullable String findYamlParentKeyForIndent(YAMLFile file, int offset, int indent) {
        for (YAMLDocument document : file.getDocuments()) {
            YAMLKeyValue match = findYamlParentKeyForIndent(document.getTopLevelValue(), offset, indent);
            if (match != null) {
                return buildYamlPropertyKey(match);
            }
        }
        return null;
    }

    private static @Nullable YAMLKeyValue findYamlParentKeyForIndent(@Nullable PsiElement element, int offset, int indent) {
        if (!(element instanceof org.jetbrains.yaml.psi.YAMLMapping mapping)) {
            return null;
        }
        YAMLKeyValue candidate = null;
        for (YAMLKeyValue keyValue : mapping.getKeyValues()) {
            if (keyValue.getTextRange().getStartOffset() >= offset) {
                break;
            }
            int currentIndent = keyValue.getTextRange().getStartOffset()
                    - keyValue.getContainingFile().getText().lastIndexOf('\n', Math.max(0, keyValue.getTextRange().getStartOffset() - 1)) - 1;
            if (currentIndent < indent) {
                candidate = keyValue;
            }
            PsiElement nested = keyValue.getValue();
            YAMLKeyValue nestedCandidate = findYamlParentKeyForIndent(nested, offset, indent);
            if (nestedCandidate != null) {
                candidate = nestedCandidate;
            }
        }
        return candidate;
    }

    public static final class YamlCompletionContext {
        private final String parentKey;
        private final String typedPrefix;
        private final boolean flattenedKey;

        public YamlCompletionContext(@Nullable String parentKey, @Nullable String typedPrefix, boolean flattenedKey) {
            this.parentKey = parentKey;
            this.typedPrefix = typedPrefix;
            this.flattenedKey = flattenedKey;
        }

        public @Nullable String getParentKey() {
            return parentKey;
        }

        public @Nullable String getTypedPrefix() {
            return typedPrefix;
        }

        public boolean isFlattenedKey() {
            return flattenedKey;
        }
    }
}
