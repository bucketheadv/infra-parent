package io.infra.idea.plugin.gospring.inspection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared GORM tag validation logic for both inspection and annotator.
 */
public final class GoSpringGormTagSupport {
    private static final Pattern GORM_TAG_PATTERN = Pattern.compile("(^|\\s)gorm:\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_ ]*|<-|->|-");
    private static final Pattern TYPE_ALLOWED_PATTERN = Pattern.compile("[A-Za-z0-9_(),.\\-\\s]+");
    private static final Pattern TYPE_PARAMETER_PATTERN = Pattern.compile("[0-9]+(?:\\s*,\\s*[0-9]+)?");
    private static final Set<String> SUPPORTED_DIRECTIVES = Set.of(
            "-", "all", "migration", "->", "<-", "column", "type", "size", "serializer",
            "primarykey", "primary_key", "autoincrement", "autoincrementincrement",
            "embedded", "embeddedprefix", "not null", "unique", "default", "comment",
            "index", "uniqueindex", "check", "constraint", "many2many", "foreignkey",
            "references", "joinforeignkey", "joinreferences", "polymorphic", "polymorphicvalue",
            "autocreatetime", "autoupdatetime", "precision", "scale", "sort", "collate",
            "class", "where", "expression", "option", "priority", "length", "composite"
    );
    private static final Set<String> VALUE_REQUIRED_DIRECTIVES = Set.of(
            "column", "type", "size", "serializer", "embeddedprefix", "default", "comment",
            "check", "constraint", "many2many", "foreignkey", "references", "joinforeignkey",
            "joinreferences", "polymorphic", "polymorphicvalue", "precision", "scale",
            "where", "expression", "option", "priority", "length", "composite"
    );
    private static final List<String> DIRECTIVE_COMPLETIONS = List.of(
            "column:", "type:", "size:", "serializer:", "primaryKey", "primary_key",
            "autoIncrement", "embedded", "embeddedPrefix:", "not null", "unique",
            "default:", "comment:", "index", "uniqueIndex", "check:", "constraint:",
            "many2many:", "foreignKey:", "references:", "joinForeignKey:", "joinReferences:",
            "polymorphic:", "polymorphicValue:", "autoCreateTime", "autoUpdateTime",
            "precision:", "scale:", "sort:", "collate:", "where:", "expression:",
            "option:", "priority:", "length:", "composite:", "->", "<-", "-"
    );
    private static final List<String> TYPE_COMPLETIONS = List.of(
            "bigint", "bigint unsigned", "binary", "blob", "bool", "boolean",
            "char", "date", "datetime", "decimal", "double", "float",
            "int", "int unsigned", "integer", "json", "jsonb", "longblob", "longtext",
            "mediumblob", "mediumint", "mediumtext", "numeric", "real", "smallint",
            "text", "time", "timestamp", "tinyblob", "tinyint", "tinytext", "uuid",
            "varbinary", "varchar"
    );
    private static final Set<String> KNOWN_BASE_TYPES = Set.of(
            "bigint", "bigint unsigned", "binary", "blob", "bool", "boolean", "char",
            "date", "datetime", "decimal", "double", "float", "int", "int unsigned",
            "integer", "json", "jsonb", "longblob", "longtext", "mediumblob",
            "mediumint", "mediumtext", "numeric", "real", "smallint", "text",
            "time", "timestamp", "tinyblob", "tinyint", "tinytext", "uuid",
            "varbinary", "varchar"
    );
    private static final Set<String> PARAMETER_REQUIRED_BASE_TYPES = Set.of(
            "binary", "char", "decimal", "numeric", "varbinary", "varchar"
    );

    private GoSpringGormTagSupport() {
    }

    public static @NotNull List<Issue> validate(@NotNull PsiElement element) {
        String literalText = element.getText();
        if (!looksLikeGoTagLiteral(literalText)) {
            return List.of();
        }
        String content = unquote(literalText);
        if (content == null || !content.contains("gorm:\"")) {
            return List.of();
        }
        List<Issue> issues = new ArrayList<>();
        Matcher matcher = GORM_TAG_PATTERN.matcher(content);
        List<TagOccurrence> occurrences = new ArrayList<>();
        while (matcher.find()) {
            occurrences.add(new TagOccurrence(matcher.start(2), matcher.end(2), matcher.group(2)));
        }
        if (occurrences.isEmpty()) {
            return List.of();
        }
        if (occurrences.size() > 1) {
            for (int i = 1; i < occurrences.size(); i++) {
                TagOccurrence duplicate = occurrences.get(i);
                issues.add(issue(element, duplicate.startOffset, duplicate.endOffset, "同一个 struct tag 中只应出现一个 gorm 标签"));
            }
        }
        for (TagOccurrence occurrence : occurrences) {
            validateSingleTag(element, occurrence, issues);
        }
        return issues;
    }

    public static @NotNull List<HighlightTarget> collectHighlights(@NotNull PsiElement element) {
        String literalText = element.getText();
        if (!looksLikeGoTagLiteral(literalText)) {
            return List.of();
        }
        String content = unquote(literalText);
        if (content == null || !content.contains("gorm:\"")) {
            return List.of();
        }
        List<HighlightTarget> highlights = new ArrayList<>();
        Matcher matcher = GORM_TAG_PATTERN.matcher(content);
        while (matcher.find()) {
            TagOccurrence occurrence = new TagOccurrence(matcher.start(2), matcher.end(2), matcher.group(2));
            collectHighlightsForOccurrence(element, occurrence, highlights);
        }
        return highlights;
    }

    private static void validateSingleTag(PsiElement element, TagOccurrence occurrence, List<Issue> issues) {
        if (occurrence.value.isBlank()) {
            issues.add(issue(element, occurrence.startOffset, occurrence.endOffset, "gorm 标签不能为空"));
            return;
        }
        List<Segment> segments = splitSegments(occurrence);
        if (segments.isEmpty()) {
            issues.add(issue(element, occurrence.startOffset, occurrence.endOffset, "gorm 标签不能为空"));
            return;
        }
        for (Segment segment : segments) {
            validateSegment(element, segment, issues);
        }
    }

    private static void collectHighlightsForOccurrence(PsiElement element, TagOccurrence occurrence, List<HighlightTarget> highlights) {
        if (occurrence.value.isBlank()) {
            return;
        }
        for (Segment segment : splitSegments(occurrence)) {
            collectHighlightForSegment(element, segment, highlights);
        }
    }

    private static void validateSegment(PsiElement element, Segment segment, List<Issue> issues) {
        String trimmed = segment.value.trim();
        if (trimmed.isEmpty()) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm 标签中不允许出现空片段，请检查多余的分号"));
            return;
        }

        int colonIndex = findUnescaped(trimmed, ':');
        int commaIndex = findUnescaped(trimmed, ',');
        int splitIndex = firstPositive(colonIndex, commaIndex);
        String directive = splitIndex < 0 ? trimmed : trimmed.substring(0, splitIndex).trim();
        if (directive.isEmpty()) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm 标签缺少指令名"));
            return;
        }

        String normalizedDirective = directive.toLowerCase(Locale.ROOT);
        if (!isSupportedDirective(normalizedDirective)) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "未知的 gorm 指令: " + directive));
            return;
        }

        if (!IDENTIFIER_PATTERN.matcher(directive).matches() && !directive.contains(" ")) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm 指令格式不合法: " + directive));
            return;
        }

        if (normalizedDirective.equals("index") || normalizedDirective.equals("uniqueindex")) {
            validateIndexSegment(element, segment, trimmed, colonIndex, issues);
            return;
        }

        if (VALUE_REQUIRED_DIRECTIVES.contains(normalizedDirective)) {
            if (colonIndex < 0) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm 指令 " + directive + " 需要使用 key:value 形式"));
                return;
            }
            String valuePart = trimmed.substring(colonIndex + 1).trim();
            if (valuePart.isEmpty()) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm 指令 " + directive + " 的值不能为空"));
                return;
            }
            if ("type".equals(normalizedDirective)) {
                validateTypeValue(element, segment, valuePart, issues);
            }
            return;
        }

        if (colonIndex >= 0) {
            String valuePart = trimmed.substring(colonIndex + 1).trim();
            if (valuePart.isEmpty()) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm 指令 " + directive + " 的值不能为空"));
            }
        }
    }

    private static void collectHighlightForSegment(PsiElement element, Segment segment, List<HighlightTarget> highlights) {
        String trimmed = segment.value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        List<Issue> issues = new ArrayList<>();
        validateSegment(element, segment, issues);
        if (!issues.isEmpty()) {
            return;
        }

        int directiveStartOffset = findTrimmedStartOffset(segment.value);
        int colonIndex = findUnescaped(trimmed, ':');
        int directiveEndOffset = colonIndex >= 0 ? colonIndex : trimmed.length();
        highlights.add(highlight(
                element,
                segment.startOffset + directiveStartOffset,
                segment.startOffset + directiveStartOffset + directiveEndOffset,
                HighlightKind.DIRECTIVE
        ));

        if (colonIndex >= 0) {
            String directive = trimmed.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            int valueStartInTrimmed = colonIndex + 1;
            while (valueStartInTrimmed < trimmed.length() && Character.isWhitespace(trimmed.charAt(valueStartInTrimmed))) {
                valueStartInTrimmed++;
            }
            if (valueStartInTrimmed < trimmed.length()) {
                highlights.add(highlight(
                        element,
                        segment.startOffset + directiveStartOffset + valueStartInTrimmed,
                        segment.startOffset + directiveStartOffset + trimmed.length(),
                        "type".equals(directive) ? HighlightKind.TYPE_VALUE : HighlightKind.VALUE
                ));
            }
        }
    }

    private static void validateIndexSegment(PsiElement element, Segment segment, String trimmed, int colonIndex, List<Issue> issues) {
        if (colonIndex < 0) {
            return;
        }
        String payload = trimmed.substring(colonIndex + 1);
        List<String> chunks = splitUnescaped(payload, ',');
        if (chunks.isEmpty()) {
            return;
        }
        for (int i = 1; i < chunks.size(); i++) {
            String chunk = chunks.get(i).trim();
            if (chunk.isEmpty()) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "index 配置中存在空参数"));
                return;
            }
            int optionColon = findUnescaped(chunk, ':');
            if (optionColon == 0) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "index 配置参数缺少名称"));
                return;
            }
            if (optionColon > 0) {
                String optionKey = chunk.substring(0, optionColon).trim();
                if (optionKey.isEmpty()) {
                    issues.add(issue(element, segment.startOffset, segment.endOffset, "index 配置参数缺少名称"));
                    return;
                }
            }
        }
    }

    private static Issue issue(PsiElement element, int contentStart, int contentEnd, String message) {
        return new Issue(toLiteralRange(element, contentStart, contentEnd), message);
    }

    private static HighlightTarget highlight(PsiElement element, int contentStart, int contentEnd, HighlightKind kind) {
        return new HighlightTarget(toLiteralRange(element, contentStart, contentEnd), kind);
    }

    private static TextRange toLiteralRange(PsiElement element, int contentStart, int contentEnd) {
        int literalStartOffset = 1;
        int start = Math.max(literalStartOffset, contentStart + literalStartOffset);
        int end = Math.max(start + 1, contentEnd + literalStartOffset);
        int safeEnd = Math.min(end, element.getTextLength() - 1);
        if (safeEnd <= start) {
            safeEnd = Math.min(element.getTextLength(), start + 1);
        }
        return TextRange.create(start, safeEnd);
    }

    private static List<Segment> splitSegments(TagOccurrence occurrence) {
        List<Segment> segments = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < occurrence.value.length(); index++) {
            char ch = occurrence.value.charAt(index);
            if (ch == '\\') {
                index++;
                continue;
            }
            if (ch == ';') {
                segments.add(new Segment(
                        occurrence.startOffset + start,
                        occurrence.startOffset + index,
                        occurrence.value.substring(start, index)
                ));
                start = index + 1;
            }
        }
        segments.add(new Segment(
                occurrence.startOffset + start,
                occurrence.startOffset + occurrence.value.length(),
                occurrence.value.substring(start)
        ));
        return segments;
    }

    private static List<String> splitUnescaped(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\\' && index + 1 < value.length()) {
                current.append(ch).append(value.charAt(index + 1));
                index++;
                continue;
            }
            if (ch == delimiter) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        parts.add(current.toString());
        return parts;
    }

    private static int findUnescaped(String value, char target) {
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\\') {
                index++;
                continue;
            }
            if (ch == target) {
                return index;
            }
        }
        return -1;
    }

    private static int firstPositive(int left, int right) {
        if (left < 0) {
            return right;
        }
        if (right < 0) {
            return left;
        }
        return Math.min(left, right);
    }

    private static int findTrimmedStartOffset(String value) {
        int offset = 0;
        while (offset < value.length() && Character.isWhitespace(value.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private static boolean isSupportedDirective(String normalizedDirective) {
        if (SUPPORTED_DIRECTIVES.contains(normalizedDirective)) {
            return true;
        }
        return normalizedDirective.startsWith("index") || normalizedDirective.startsWith("uniqueindex");
    }

    private static void validateTypeValue(PsiElement element, Segment segment, String valuePart, List<Issue> issues) {
        String normalizedValue = valuePart.trim();
        if (!TYPE_ALLOWED_PATTERN.matcher(normalizedValue).matches()) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 值包含非法字符: " + valuePart));
            return;
        }
        int depth = 0;
        boolean hasParenthesis = false;
        int leftParenthesis = -1;
        int rightParenthesis = -1;
        for (int index = 0; index < normalizedValue.length(); index++) {
            char ch = normalizedValue.charAt(index);
            if (ch == '(') {
                hasParenthesis = true;
                if (depth > 0) {
                    issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 不支持嵌套括号: " + valuePart));
                    return;
                }
                depth++;
                leftParenthesis = index;
                continue;
            }
            if (ch == ')') {
                if (depth == 0) {
                    issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 括号不匹配: " + valuePart));
                    return;
                }
                depth--;
                rightParenthesis = index;
            }
        }
        if (depth != 0) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 括号不匹配: " + valuePart));
            return;
        }
        if (hasParenthesis) {
            if (leftParenthesis + 1 == rightParenthesis) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 括号内不能为空: " + valuePart));
                return;
            }
            String parameterPart = normalizedValue.substring(leftParenthesis + 1, rightParenthesis).trim();
            if (parameterPart.isEmpty()) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 括号内不能为空: " + valuePart));
                return;
            }
            if (!TYPE_PARAMETER_PATTERN.matcher(parameterPart).matches()) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 参数格式不合法: " + valuePart));
                return;
            }
            String suffix = normalizedValue.substring(rightParenthesis + 1).trim();
            if (!suffix.isEmpty() && !suffix.matches("[A-Za-z][A-Za-z0-9_\\s]*")) {
                issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 后缀格式不合法: " + valuePart));
                return;
            }
        }
        String baseType = extractBaseType(normalizedValue);
        if (baseType == null || baseType.isBlank()) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type 缺少有效类型名"));
            return;
        }
        String normalizedBaseType = baseType.toLowerCase(Locale.ROOT);
        if (!KNOWN_BASE_TYPES.contains(normalizedBaseType)) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "未知的 gorm type: " + valuePart));
            return;
        }
        if (PARAMETER_REQUIRED_BASE_TYPES.contains(normalizedBaseType) && !hasParenthesis) {
            issues.add(issue(element, segment.startOffset, segment.endOffset, "gorm type " + baseType + " 需要指定长度或精度参数"));
        }
    }

    private static @Nullable String extractBaseType(String value) {
        String normalized = value.trim();
        int parenthesisIndex = normalized.indexOf('(');
        if (parenthesisIndex >= 0) {
            return normalized.substring(0, parenthesisIndex).trim();
        }
        return normalized;
    }

    public static @NotNull List<String> directiveCompletions() {
        return DIRECTIVE_COMPLETIONS;
    }

    public static @NotNull List<String> typeCompletions() {
        return TYPE_COMPLETIONS;
    }

    public static @Nullable CompletionContext completionContext(@NotNull PsiElement element, int absoluteOffset) {
        String literalText = element.getText();
        if (!looksLikeGoTagLiteral(literalText)) {
            return null;
        }
        String content = unquote(literalText);
        if (content == null) {
            return null;
        }
        int literalOffset = absoluteOffset - element.getTextRange().getStartOffset() - 1;
        if (literalOffset < 0 || literalOffset > content.length()) {
            return null;
        }
        Matcher matcher = GORM_TAG_PATTERN.matcher(content);
        while (matcher.find()) {
            int valueStart = matcher.start(2);
            int valueEnd = matcher.end(2);
            if (literalOffset < valueStart || literalOffset > valueEnd) {
                continue;
            }
            String gormValue = matcher.group(2);
            int relativeOffset = Math.max(0, Math.min(gormValue.length(), literalOffset - valueStart));
            int segmentStart = lastIndexOfUnescaped(gormValue, ';', relativeOffset - 1) + 1;
            int segmentEnd = nextIndexOfUnescaped(gormValue, ';', relativeOffset);
            if (segmentEnd < 0) {
                segmentEnd = gormValue.length();
            }
            String segment = gormValue.substring(segmentStart, segmentEnd);
            int caretInSegment = Math.max(0, Math.min(segment.length(), relativeOffset - segmentStart));
            int colonIndex = findUnescaped(segment, ':');
            if (colonIndex >= 0 && caretInSegment > colonIndex) {
                String directive = segment.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
                if ("type".equals(directive)) {
                    String valuePrefix = segment.substring(colonIndex + 1, caretInSegment).trim();
                    return new CompletionContext(CompletionKind.TYPE_VALUE, valuePrefix);
                }
            }
            String directivePrefix = segment.substring(0, caretInSegment).trim();
            return new CompletionContext(CompletionKind.DIRECTIVE, directivePrefix);
        }
        return null;
    }

    private static int lastIndexOfUnescaped(String value, char target, int fromIndex) {
        for (int index = fromIndex; index >= 0; index--) {
            if (value.charAt(index) == target && !isEscaped(value, index)) {
                return index;
            }
        }
        return -1;
    }

    private static int nextIndexOfUnescaped(String value, char target, int fromIndex) {
        for (int index = Math.max(0, fromIndex); index < value.length(); index++) {
            if (value.charAt(index) == target && !isEscaped(value, index)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isEscaped(String value, int index) {
        int slashCount = 0;
        for (int cursor = index - 1; cursor >= 0 && value.charAt(cursor) == '\\'; cursor--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private static boolean looksLikeGoTagLiteral(@Nullable String text) {
        if (text == null || text.length() < 2) {
            return false;
        }
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if (!((first == '`' && last == '`') || (first == '"' && last == '"'))) {
            return false;
        }
        return text.contains("gorm:\"");
    }

    private static @Nullable String unquote(String text) {
        if (!looksLikeGoTagLiteral(text)) {
            return null;
        }
        return text.substring(1, text.length() - 1);
    }

    public static final class Issue {
        private final TextRange rangeInElement;
        private final String message;

        public Issue(TextRange rangeInElement, String message) {
            this.rangeInElement = rangeInElement;
            this.message = message;
        }

        public TextRange getRangeInElement() {
            return rangeInElement;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class HighlightTarget {
        private final TextRange rangeInElement;
        private final HighlightKind kind;

        public HighlightTarget(TextRange rangeInElement, HighlightKind kind) {
            this.rangeInElement = rangeInElement;
            this.kind = kind;
        }

        public TextRange getRangeInElement() {
            return rangeInElement;
        }

        public HighlightKind getKind() {
            return kind;
        }
    }

    public enum HighlightKind {
        DIRECTIVE,
        VALUE,
        TYPE_VALUE
    }

    public enum CompletionKind {
        DIRECTIVE,
        TYPE_VALUE
    }

    public static final class CompletionContext {
        private final CompletionKind kind;
        private final String prefix;

        public CompletionContext(CompletionKind kind, String prefix) {
            this.kind = kind;
            this.prefix = prefix == null ? "" : prefix;
        }

        public CompletionKind getKind() {
            return kind;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    private static final class TagOccurrence {
        private final int startOffset;
        private final int endOffset;
        private final String value;

        private TagOccurrence(int startOffset, int endOffset, String value) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.value = value;
        }
    }

    private static final class Segment {
        private final int startOffset;
        private final int endOffset;
        private final String value;

        private Segment(int startOffset, int endOffset, String value) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.value = value;
        }
    }
}
