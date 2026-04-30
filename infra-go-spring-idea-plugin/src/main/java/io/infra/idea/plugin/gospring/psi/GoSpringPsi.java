package io.infra.idea.plugin.gospring.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoSpringPsi {
    private static final Pattern AUTOWIRE_TAG = Pattern.compile("(^|\\s)autowire:\"([^\"]*)\"");
    private static final Pattern VALUE_TAG = Pattern.compile("(^|\\s)value:\"\\$\\{([^}:]+)(?::=[^}]*)?}\"");
    private static final Pattern GORM_TAG = Pattern.compile("(^|\\s)gorm:\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern FIELD_PREFIX_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s+(.+?)\\s*$");

    private GoSpringPsi() {
    }

    public static boolean isGoFile(@Nullable PsiElement element) {
        PsiFile file = element == null ? null : element.getContainingFile();
        if (file == null || file.getVirtualFile() == null) {
            return false;
        }
        if ("go".equalsIgnoreCase(file.getVirtualFile().getExtension())) {
            return true;
        }
        return "go".equalsIgnoreCase(file.getLanguage().getID());
    }

    public static boolean isSupportedConfigFile(@Nullable PsiFile file) {
        if (file == null || file.getName() == null) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return "app.properties".equals(name) || "app.yml".equals(name) || "app.yaml".equals(name);
    }

    public static @Nullable PsiElement findStringLiteral(@Nullable PsiElement sourceElement) {
        PsiElement cursor = sourceElement;
        while (cursor != null) {
            String text = cursor.getText();
            if (isStringLiteral(text) && text.length() <= 2048) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    public static List<TagMatch> findTagMatches(@Nullable PsiElement stringLiteral) {
        if (stringLiteral == null) {
            return List.of();
        }
        String literalText = stringLiteral.getText();
        if (literalText == null || literalText.length() < 2 || literalText.charAt(0) != '`' || literalText.charAt(literalText.length() - 1) != '`') {
            return List.of();
        }
        String content = literalText.substring(1, literalText.length() - 1);
        List<TagMatch> result = new ArrayList<>();

        Matcher autowireMatcher = AUTOWIRE_TAG.matcher(content);
        while (autowireMatcher.find()) {
            collectAutowireMatches(autowireMatcher.group(2), autowireMatcher.start(2) + 1, result);
        }

        Matcher valueMatcher = VALUE_TAG.matcher(content);
        while (valueMatcher.find()) {
            String rawKey = valueMatcher.group(2);
            if (rawKey != null && !rawKey.isBlank()) {
                int leadingWhitespace = countLeadingWhitespace(rawKey);
                int trailingWhitespace = countTrailingWhitespace(rawKey);
                int meaningfulLength = rawKey.length() - leadingWhitespace - trailingWhitespace;
                if (meaningfulLength <= 0) {
                    continue;
                }
                String key = rawKey.substring(leadingWhitespace, rawKey.length() - trailingWhitespace);
                result.add(new TagMatch(
                        ReferenceKind.VALUE,
                        key,
                        TextRange.from(valueMatcher.start(2) + 1 + leadingWhitespace, meaningfulLength)
                ));
            }
        }
        return result;
    }

    public static List<GormColumnTagMatch> findGormColumnTagMatches(@Nullable PsiElement stringLiteral) {
        if (stringLiteral == null) {
            return List.of();
        }
        String literalText = stringLiteral.getText();
        if (literalText == null || literalText.length() < 2 || literalText.charAt(0) != '`' || literalText.charAt(literalText.length() - 1) != '`') {
            return List.of();
        }
        String content = literalText.substring(1, literalText.length() - 1);
        List<GormColumnTagMatch> matches = new ArrayList<>();
        Matcher gormMatcher = GORM_TAG.matcher(content);
        while (gormMatcher.find()) {
            String gormValue = gormMatcher.group(2);
            if (gormValue == null || gormValue.isBlank()) {
                continue;
            }
            int valueStartInLiteral = gormMatcher.start(2) + 1;
            collectGormColumnTagMatches(gormValue, valueStartInLiteral, matches);
        }
        return matches;
    }

    public static @Nullable TagMatch findTagMatchAtOffset(@Nullable PsiElement sourceElement, int offset) {
        PsiElement stringLiteral = findStringLiteral(sourceElement);
        if (stringLiteral == null) {
            return null;
        }
        int relativeOffset = offset - stringLiteral.getTextRange().getStartOffset();
        if (relativeOffset < 0 || relativeOffset >= stringLiteral.getTextLength()) {
            return null;
        }
        for (TagMatch match : findTagMatches(stringLiteral)) {
            if (match.getRange().containsOffset(relativeOffset)) {
                return match;
            }
        }
        return null;
    }

    public static @Nullable BeanDefinitionMatch findBeanDefinitionAtOffset(@Nullable PsiElement sourceElement, int offset) {
        PsiElement stringLiteral = findStringLiteral(sourceElement);
        if (stringLiteral == null) {
            return null;
        }
        BeanDefinitionMatch match = getBeanDefinitionMatch(stringLiteral);
        if (match == null) {
            return null;
        }
        int relativeOffset = offset - stringLiteral.getTextRange().getStartOffset();
        return match.getRange().containsOffset(relativeOffset) ? match : null;
    }

    public static @Nullable AutowireNavigation findAutowireNavigationAtOffset(@Nullable PsiElement sourceElement, int offset) {
        PsiElement stringLiteral = findStringLiteral(sourceElement);
        if (stringLiteral == null) {
            return null;
        }
        String literalText = stringLiteral.getText();
        if (literalText == null || literalText.length() < 2 || literalText.charAt(0) != '`' || literalText.charAt(literalText.length() - 1) != '`') {
            return null;
        }
        int relativeOffset = offset - stringLiteral.getTextRange().getStartOffset();
        if (relativeOffset < 0 || relativeOffset >= stringLiteral.getTextLength()) {
            return null;
        }

        String fieldType = extractFieldType(stringLiteral);
        if (fieldType == null || fieldType.isBlank()) {
            return null;
        }

        String content = literalText.substring(1, literalText.length() - 1);
        Matcher matcher = AUTOWIRE_TAG.matcher(content);
        while (matcher.find()) {
            String expression = matcher.group(2);
            int expressionStart = matcher.start(2) + 1;
            int expressionEnd = expressionStart + (expression == null ? 0 : expression.length());
            if (!isInsideAutowireValue(relativeOffset, expressionStart, expressionEnd)) {
                continue;
            }
            return buildAutowireNavigation(fieldType, expression, expressionStart, relativeOffset);
        }
        return null;
    }

    public static @Nullable BeanDefinitionMatch getBeanDefinitionMatch(@Nullable PsiElement stringLiteral) {
        if (stringLiteral == null) {
            return null;
        }
        String literalText = stringLiteral.getText();
        if (!isStringLiteral(literalText) || literalText.charAt(0) == '`') {
            return null;
        }
        String beanName = unquote(literalText);
        if (beanName == null || beanName.isBlank()) {
            return null;
        }
        String context = collectAncestorText(stringLiteral, 6, 4096);
        int nameCallOffset = context.lastIndexOf(".Name(");
        if (nameCallOffset < 0 || !looksLikeBeanRegistration(context, nameCallOffset)) {
            return null;
        }
        return new BeanDefinitionMatch(beanName, TextRange.from(1, beanName.length()));
    }

    private static void collectAutowireMatches(String expression, int startOffsetInLiteral, List<TagMatch> result) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        int segmentStart = 0;
        for (int i = 0; i <= expression.length(); i++) {
            if (i < expression.length() && expression.charAt(i) != ',') {
                continue;
            }
            String segment = expression.substring(segmentStart, i);
            int leadingWhitespace = countLeadingWhitespace(segment);
            int trailingWhitespace = countTrailingWhitespace(segment);
            String trimmed = segment.trim();
            if (!trimmed.isEmpty() && !"?".equals(trimmed) && !"*?".equals(trimmed) && !"*".equals(trimmed)) {
                String beanName = trimmed.endsWith("?") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
                if (!beanName.isBlank() && !beanName.contains("*")) {
                    int absoluteStart = startOffsetInLiteral + segmentStart + leadingWhitespace;
                    int length = segment.length() - leadingWhitespace - trailingWhitespace;
                    if (trimmed.endsWith("?")) {
                        length -= 1;
                    }
                    result.add(new TagMatch(ReferenceKind.AUTOWIRE, beanName, TextRange.from(absoluteStart, length)));
                }
            }
            segmentStart = i + 1;
        }
    }

    private static int countLeadingWhitespace(String text) {
        int count = 0;
        while (count < text.length() && Character.isWhitespace(text.charAt(count))) {
            count++;
        }
        return count;
    }

    private static int countTrailingWhitespace(String text) {
        int count = 0;
        while (count < text.length() && Character.isWhitespace(text.charAt(text.length() - 1 - count))) {
            count++;
        }
        return count;
    }

    private static void collectGormColumnTagMatches(String gormValue,
                                                    int valueStartInLiteral,
                                                    List<GormColumnTagMatch> matches) {
        int segmentStart = 0;
        for (int i = 0; i <= gormValue.length(); i++) {
            if (i < gormValue.length() && gormValue.charAt(i) != ';') {
                continue;
            }
            String segment = gormValue.substring(segmentStart, i);
            int leftTrim = countLeadingWhitespace(segment);
            int rightTrim = countTrailingWhitespace(segment);
            int meaningfulEnd = segment.length() - rightTrim;
            if (leftTrim < meaningfulEnd) {
                String trimmed = segment.substring(leftTrim, meaningfulEnd);
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex > 0) {
                    String key = trimmed.substring(0, colonIndex).trim();
                    if ("column".equalsIgnoreCase(key)) {
                        int valueStart = colonIndex + 1;
                        while (valueStart < trimmed.length() && Character.isWhitespace(trimmed.charAt(valueStart))) {
                            valueStart++;
                        }
                        int valueEnd = trimmed.length();
                        while (valueEnd > valueStart && Character.isWhitespace(trimmed.charAt(valueEnd - 1))) {
                            valueEnd--;
                        }
                        if (valueEnd > valueStart) {
                            String columnValue = trimmed.substring(valueStart, valueEnd);
                            int rangeStart = valueStartInLiteral + segmentStart + leftTrim + valueStart;
                            matches.add(new GormColumnTagMatch(columnValue, TextRange.from(rangeStart, valueEnd - valueStart)));
                        }
                    }
                }
            }
            segmentStart = i + 1;
        }
    }

    private static boolean isStringLiteral(@Nullable String text) {
        return text != null
                && text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("`") && text.endsWith("`")));
    }

    private static @Nullable String unquote(String text) {
        if (!isStringLiteral(text)) {
            return null;
        }
        return text.substring(1, text.length() - 1);
    }

    private static boolean isInsideAutowireValue(int relativeOffset, int expressionStart, int expressionEnd) {
        if (expressionStart == expressionEnd) {
            return relativeOffset == expressionStart || relativeOffset == expressionStart - 1;
        }
        return relativeOffset >= expressionStart && relativeOffset <= expressionEnd;
    }

    private static @Nullable AutowireNavigation buildAutowireNavigation(String fieldType,
                                                                        @Nullable String expression,
                                                                        int expressionStart,
                                                                        int relativeOffset) {
        String normalized = expression == null ? "" : expression;
        List<String> explicitBeanNames = collectExplicitBeanNames(normalized);
        if (normalized.isBlank() || "?".equals(normalized.trim())) {
            return new AutowireNavigation(fieldType, null, explicitBeanNames, false);
        }

        int segmentStart = 0;
        for (int i = 0; i <= normalized.length(); i++) {
            if (i < normalized.length() && normalized.charAt(i) != ',') {
                continue;
            }
            String segment = normalized.substring(segmentStart, i);
            int absoluteStart = expressionStart + segmentStart;
            int absoluteEnd = absoluteStart + segment.length();
            if (relativeOffset < absoluteStart || relativeOffset > absoluteEnd) {
                segmentStart = i + 1;
                continue;
            }
            String trimmed = segment.trim();
            if (trimmed.isEmpty() || "?".equals(trimmed)) {
                return new AutowireNavigation(fieldType, null, explicitBeanNames, false);
            }
            if ("*".equals(trimmed) || "*?".equals(trimmed)) {
                return new AutowireNavigation(fieldType, null, explicitBeanNames, true);
            }
            String beanName = trimmed.endsWith("?") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            if (!beanName.isBlank() && !beanName.contains("*")) {
                return new AutowireNavigation(fieldType, beanName, explicitBeanNames, false);
            }
            segmentStart = i + 1;
        }

        if (normalized.contains("*")) {
            return new AutowireNavigation(fieldType, null, explicitBeanNames, true);
        }
        return new AutowireNavigation(fieldType, null, explicitBeanNames, false);
    }

    private static List<String> collectExplicitBeanNames(String expression) {
        List<String> result = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            return result;
        }
        int segmentStart = 0;
        for (int i = 0; i <= expression.length(); i++) {
            if (i < expression.length() && expression.charAt(i) != ',') {
                continue;
            }
            String trimmed = expression.substring(segmentStart, i).trim();
            if (!trimmed.isEmpty() && !"?".equals(trimmed) && !"*".equals(trimmed) && !"*?".equals(trimmed)) {
                String beanName = trimmed.endsWith("?") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
                if (!beanName.isBlank() && !beanName.contains("*")) {
                    result.add(beanName);
                }
            }
            segmentStart = i + 1;
        }
        return result;
    }

    private static @Nullable String extractFieldType(PsiElement stringLiteral) {
        PsiFile file = stringLiteral.getContainingFile();
        if (file == null) {
            return null;
        }
        String text = file.getText();
        int literalOffset = stringLiteral.getTextRange().getStartOffset();
        int lineStart = Math.max(0, text.lastIndexOf('\n', Math.max(0, literalOffset - 1)) + 1);
        String linePrefix = text.substring(lineStart, literalOffset);
        Matcher matcher = FIELD_PREFIX_PATTERN.matcher(linePrefix);
        if (!matcher.find()) {
            return null;
        }
        String typeName = matcher.group(2);
        if (typeName == null) {
            return null;
        }
        String normalized = typeName.replaceAll("\\s+", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String collectAncestorText(@NotNull PsiElement element, int maxDepth, int maxLength) {
        PsiElement cursor = element;
        for (int i = 0; i < maxDepth && cursor != null; i++) {
            String text = cursor.getText();
            if (text != null && text.length() <= maxLength && text.contains(element.getText())) {
                return text;
            }
            cursor = cursor.getParent();
        }
        return element.getText();
    }

    private static boolean looksLikeBeanRegistration(String text, int nameCallStart) {
        int fromIndex = Math.max(0, nameCallStart - 1200);
        String lookBehind = text.substring(fromIndex, nameCallStart);
        return lookBehind.contains(".Provide(") || lookBehind.contains(".Object(") || lookBehind.contains(".Root(");
    }

    public enum ReferenceKind {
        AUTOWIRE,
        VALUE
    }

    public static final class TagMatch {
        private final ReferenceKind kind;
        private final String value;
        private final TextRange range;

        public TagMatch(ReferenceKind kind, String value, TextRange range) {
            this.kind = kind;
            this.value = value;
            this.range = range;
        }

        public ReferenceKind getKind() {
            return kind;
        }

        public String getValue() {
            return value;
        }

        public TextRange getRange() {
            return range;
        }
    }

    public static final class BeanDefinitionMatch {
        private final String beanName;
        private final TextRange range;

        public BeanDefinitionMatch(String beanName, TextRange range) {
            this.beanName = beanName;
            this.range = range;
        }

        public String getBeanName() {
            return beanName;
        }

        public TextRange getRange() {
            return range;
        }
    }

    public static final class AutowireNavigation {
        private final String typeName;
        private final String beanName;
        private final List<String> explicitBeanNames;
        private final boolean wildcard;

        public AutowireNavigation(String typeName, @Nullable String beanName, List<String> explicitBeanNames, boolean wildcard) {
            this.typeName = typeName;
            this.beanName = beanName;
            this.explicitBeanNames = List.copyOf(explicitBeanNames);
            this.wildcard = wildcard;
        }

        public String getTypeName() {
            return typeName;
        }

        public @Nullable String getBeanName() {
            return beanName;
        }

        public List<String> getExplicitBeanNames() {
            return explicitBeanNames;
        }

        public boolean isWildcard() {
            return wildcard;
        }
    }

    public static final class GormColumnTagMatch {
        private final String columnValue;
        private final TextRange range;

        public GormColumnTagMatch(String columnValue, TextRange range) {
            this.columnValue = columnValue;
            this.range = range;
        }

        public String getColumnValue() {
            return columnValue;
        }

        public TextRange getRange() {
            return range;
        }
    }
}
