package io.infra.idea.plugin.gospring.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import io.infra.idea.plugin.gospring.model.GoSpringGormFieldDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGormQueryUsage;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Index for GORM query string field references and model field mappings.
 */
public final class GoSpringGormQueryIndex {
    private static final Key<CachedValue<Model>> CACHE_KEY = Key.create("infra.goSpring.gormQueryModel");
    private static final Pattern STRUCT_PATTERN = Pattern.compile("type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+struct\\s*\\{([\\s\\S]*?)\\n\\}");
    private static final Pattern FIELD_PREFIX_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s+(.+?)\\s*$");
    private static final Pattern GORM_TAG_VALUE_PATTERN = Pattern.compile("(^|\\s)gorm:\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern GORM_COLUMN_DIRECTIVE_PATTERN = Pattern.compile("(^|;)\\s*column\\s*:\\s*([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"|`([^`]*)`");
    private static final Pattern QUERY_METHOD_PATTERN = Pattern.compile("\\.(Where|Order|OrderBy|Select|Joins|Group|Having)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL_PATTERN = Pattern.compile("\\.Model\\s*\\(\\s*&?([A-Za-z_][A-Za-z0-9_\\.]*)\\s*(?:\\{\\s*\\})?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TERMINAL_QUERY_PATTERN = Pattern.compile(
            "\\.(First|Find|Take|Last|Delete|Update|Updates|Count)\\s*\\(\\s*([^\\)]*)\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RESULT_VAR_PATTERN = Pattern.compile("&?([A-Za-z_][A-Za-z0-9_\\.]*)");
    private static final Pattern SQL_KEYWORD_PATTERN = Pattern.compile("\\b(AND|OR|NOT|IN|IS|NULL|LIKE|BETWEEN|ASC|DESC|ORDER|BY|GROUP|HAVING|WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|ON|SELECT|DISTINCT|AS)\\b", Pattern.CASE_INSENSITIVE);

    private GoSpringGormQueryIndex() {
    }

    public static @Nullable GoSpringGormQueryUsage findUsageAt(@Nullable PsiElement sourceElement, int offset) {
        PsiElement stringLiteral = GoSpringPsi.findStringLiteral(sourceElement);
        if (stringLiteral == null) {
            return null;
        }
        int relativeOffset = offset - stringLiteral.getTextRange().getStartOffset();
        for (GoSpringGormQueryUsage usage : findUsagesInLiteral(stringLiteral)) {
            if (usage.getRangeInElement().containsOffset(relativeOffset)) {
                return usage;
            }
        }
        return null;
    }

    public static @NotNull Collection<GoSpringGormQueryUsage> findUsagesInLiteral(@Nullable PsiElement stringLiteral) {
        if (stringLiteral == null || stringLiteral.getContainingFile() == null || stringLiteral.getContainingFile().getVirtualFile() == null) {
            return List.of();
        }
        String literalIdentity = identity(stringLiteral);
        List<GoSpringGormQueryUsage> usages = getModel(stringLiteral.getProject()).queryUsagesByLiteral.get(literalIdentity);
        return usages == null ? List.of() : usages;
    }

    public static @Nullable GoSpringGormFieldDefinition findFieldDefinition(@NotNull Project project, @NotNull GoSpringGormQueryUsage usage) {
        return getModel(project).fieldByStructAndColumn.get(key(usage.getStructName(), usage.getColumnName()));
    }

    public static @Nullable GoSpringGormFieldDefinition findFieldDefinitionAt(@Nullable PsiElement sourceElement) {
        if (sourceElement == null || sourceElement.getContainingFile() == null) {
            return null;
        }
        for (GoSpringGormFieldDefinition definition : getModel(sourceElement.getProject()).allFields) {
            if (isSameAnchor(definition.getPsiElement(), sourceElement)) {
                return definition;
            }
        }
        return null;
    }

    public static @NotNull Collection<PsiElement> findUsageTargetsAt(@Nullable PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        LinkedHashSet<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringGormFieldDefinition definition : getModel(sourceElement.getProject()).allFields) {
            if (isSameAnchor(definition.getPsiElement(), sourceElement)) {
                targets.addAll(findUsageTargets(sourceElement.getProject(), definition));
            }
        }
        return targets;
    }

    public static @NotNull Collection<PsiElement> findUsageTargetsForExactAnchor(@Nullable PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        LinkedHashSet<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringGormFieldDefinition definition : getModel(sourceElement.getProject()).allFields) {
            if (definition.getPsiElement() == sourceElement) {
                targets.addAll(findUsageTargets(sourceElement.getProject(), definition));
            }
        }
        return targets;
    }

    public static @NotNull Collection<PsiElement> findUsageTargets(@NotNull Project project, @NotNull GoSpringGormFieldDefinition definition) {
        List<GoSpringGormQueryUsage> usages = getModel(project).usagesByFieldKey.getOrDefault(key(definition.getStructName(), definition.getColumnName()), List.of());
        LinkedHashSet<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringGormQueryUsage usage : usages) {
            PsiElement target = findElementAtRangeStart(usage.getStringLiteral(), usage.getRangeInElement());
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    public static @NotNull Collection<PsiElement> findUsageTargetsByColumnName(@NotNull Project project, @Nullable String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return List.of();
        }
        String normalizedColumn = columnName.trim();
        LinkedHashSet<PsiElement> targets = new LinkedHashSet<>();
        for (List<GoSpringGormQueryUsage> usages : getModel(project).usagesByFieldKey.values()) {
            for (GoSpringGormQueryUsage usage : usages) {
                if (!normalizedColumn.equalsIgnoreCase(usage.getColumnName())) {
                    continue;
                }
                PsiElement target = findElementAtRangeStart(usage.getStringLiteral(), usage.getRangeInElement());
                if (target != null) {
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    public static @NotNull Collection<PsiElement> findFieldTargetsByColumnName(@NotNull Project project, @Nullable String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return List.of();
        }
        String normalizedColumn = columnName.trim();
        LinkedHashSet<PsiElement> targets = new LinkedHashSet<>();
        for (GoSpringGormFieldDefinition definition : getModel(project).allFields) {
            if (!normalizedColumn.equalsIgnoreCase(definition.getColumnName())) {
                continue;
            }
            if (definition.getPsiElement() != null) {
                targets.add(definition.getPsiElement());
            }
        }
        return targets;
    }

    public static @NotNull Collection<TextRange> findKeywordRanges(@Nullable PsiElement stringLiteral) {
        if (stringLiteral == null) {
            return List.of();
        }
        String text = stringLiteral.getText();
        if (text == null || text.length() < 2 || !isQuotedLiteral(text)) {
            return List.of();
        }
        String content = text.substring(1, text.length() - 1);
        List<TextRange> ranges = new ArrayList<>();
        Matcher matcher = SQL_KEYWORD_PATTERN.matcher(content);
        while (matcher.find()) {
            ranges.add(TextRange.from(matcher.start() + 1, matcher.end() - matcher.start()));
        }
        return ranges;
    }

    private static boolean isQuotedLiteral(@NotNull String text) {
        if (text.length() < 2) {
            return false;
        }
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        return (first == '"' && last == '"') || (first == '`' && last == '`');
    }

    private static Model getModel(Project project) {
        CachedValuesManager manager = CachedValuesManager.getManager(project);
        return manager.getCachedValue(
                project,
                CACHE_KEY,
                () -> CachedValueProvider.Result.create(buildModel(project), PsiModificationTracker.MODIFICATION_COUNT),
                false
        );
    }

    private static Model buildModel(Project project) {
        Model model = new Model();
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project))) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                continue;
            }
            collectStructFields(psiFile, psiFile.getText(), model);
        }
        expandStructFields(model);
        for (VirtualFile file : FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project))) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                continue;
            }
            collectQueryUsages(psiFile, psiFile.getText(), model);
        }
        return model;
    }

    private static void collectStructFields(PsiFile psiFile, String text, Model model) {
        Matcher matcher = STRUCT_PATTERN.matcher(text);
        while (matcher.find()) {
            String structName = matcher.group(1);
            String structBody = matcher.group(2);
            if (structName == null || structBody == null) {
                continue;
            }
            int bodyStart = matcher.start(2);
            int cursor = 0;
            while (cursor < structBody.length()) {
                int lineEnd = structBody.indexOf('\n', cursor);
                if (lineEnd < 0) {
                    lineEnd = structBody.length();
                }
                String line = structBody.substring(cursor, lineEnd);
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                    int backtickStart = line.indexOf('`');
                    String fieldPrefix = backtickStart > 0 ? line.substring(0, backtickStart).trim() : line.trim();
                    int split = findLastWhitespace(fieldPrefix);
                    if (split > 0) {
                        String fieldName = fieldPrefix.substring(0, split).trim();
                        if (!fieldName.isBlank()) {
                            String columnName = fieldName;
                            if (backtickStart > 0) {
                                int backtickEnd = line.indexOf('`', backtickStart + 1);
                                if (backtickEnd > backtickStart) {
                                    String tagContent = line.substring(backtickStart + 1, backtickEnd);
                                    String explicitColumn = extractGormColumn(tagContent);
                                    if (explicitColumn != null && !explicitColumn.isBlank()) {
                                        columnName = explicitColumn.trim();
                                    }
                                }
                            }
                            int fieldNameOffset = line.indexOf(fieldName);
                            PsiElement anchor = findAnchor(psiFile, bodyStart + cursor + Math.max(fieldNameOffset, 0));
                            GoSpringGormFieldDefinition definition = new GoSpringGormFieldDefinition(structName, fieldName, columnName, anchor);
                            model.ownFieldsByStruct.computeIfAbsent(structName, unused -> new ArrayList<>()).add(definition);
                        }
                    } else {
                        String embeddedStruct = baseTypeName(fieldPrefix);
                        if (!embeddedStruct.isBlank()) {
                            model.embeddedStructsByStruct.computeIfAbsent(structName, unused -> new ArrayList<>()).add(embeddedStruct);
                        }
                    }
                }
                cursor = lineEnd + 1;
            }
        }
    }

    private static void expandStructFields(Model model) {
        Set<String> structNames = new LinkedHashSet<>();
        structNames.addAll(model.ownFieldsByStruct.keySet());
        structNames.addAll(model.embeddedStructsByStruct.keySet());
        Map<String, List<GoSpringGormFieldDefinition>> resolvedFields = new LinkedHashMap<>();
        for (String structName : structNames) {
            resolveStructFields(structName, model, resolvedFields, new LinkedHashSet<>());
        }
        for (Map.Entry<String, List<GoSpringGormFieldDefinition>> entry : resolvedFields.entrySet()) {
            String structName = entry.getKey();
            List<GoSpringGormFieldDefinition> fields = entry.getValue();
            model.fieldsByStruct.put(structName, fields);
            model.allFields.addAll(fields);
            for (GoSpringGormFieldDefinition field : fields) {
                model.fieldByStructAndColumn.putIfAbsent(key(structName, field.getColumnName()), field);
            }
        }
    }

    private static List<GoSpringGormFieldDefinition> resolveStructFields(String structName,
                                                                         Model model,
                                                                         Map<String, List<GoSpringGormFieldDefinition>> resolvedFields,
                                                                         Set<String> visiting) {
        List<GoSpringGormFieldDefinition> cached = resolvedFields.get(structName);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(structName)) {
            return model.ownFieldsByStruct.getOrDefault(structName, List.of());
        }

        List<GoSpringGormFieldDefinition> resolved = new ArrayList<>(model.ownFieldsByStruct.getOrDefault(structName, List.of()));
        Set<String> columnNames = new LinkedHashSet<>();
        for (GoSpringGormFieldDefinition field : resolved) {
            columnNames.add(field.getColumnName().toLowerCase(Locale.ROOT));
        }

        for (String embeddedStruct : model.embeddedStructsByStruct.getOrDefault(structName, List.of())) {
            for (GoSpringGormFieldDefinition inherited : resolveStructFields(embeddedStruct, model, resolvedFields, visiting)) {
                String columnKey = inherited.getColumnName().toLowerCase(Locale.ROOT);
                if (!columnNames.add(columnKey)) {
                    continue;
                }
                resolved.add(new GoSpringGormFieldDefinition(
                        structName,
                        inherited.getFieldName(),
                        inherited.getColumnName(),
                        inherited.getPsiElement()
                ));
            }
        }

        visiting.remove(structName);
        resolvedFields.put(structName, resolved);
        return resolved;
    }

    private static void collectQueryUsages(PsiFile psiFile, String text, Model model) {
        Matcher matcher = STRING_LITERAL_PATTERN.matcher(text);
        while (matcher.find()) {
            String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (content == null || content.isBlank()) {
                continue;
            }
            int literalStart = matcher.start();
            QueryContext context = resolveQueryContext(text, literalStart);
            if (context == null) {
                continue;
            }
            List<GoSpringGormFieldDefinition> fields = model.fieldsByStruct.getOrDefault(context.structName, List.of());
            if (fields.isEmpty()) {
                continue;
            }
            PsiElement stringLiteral = findAnchor(psiFile, literalStart);
            for (GoSpringGormFieldDefinition field : fields) {
                for (TextRange range : findColumnRanges(content, field.getColumnName())) {
                    GoSpringGormQueryUsage usage = new GoSpringGormQueryUsage(
                            context.methodName,
                            context.structName,
                            field.getColumnName(),
                            field.getFieldName(),
                            stringLiteral,
                            shiftRange(range, 1)
                    );
                    model.queryUsagesByLiteral.computeIfAbsent(identity(stringLiteral), unused -> new ArrayList<>()).add(usage);
                    model.usagesByFieldKey.computeIfAbsent(key(context.structName, field.getColumnName()), unused -> new ArrayList<>()).add(usage);
                }
            }
        }
    }

    private static @Nullable QueryContext resolveQueryContext(String text, int literalStartOffset) {
        int windowStart = Math.max(0, literalStartOffset - 800);
        String snippet = text.substring(windowStart, literalStartOffset);
        Matcher queryMatcher = QUERY_METHOD_PATTERN.matcher(snippet);
        int methodStart = -1;
        String methodName = null;
        while (queryMatcher.find()) {
            methodStart = queryMatcher.start();
            methodName = queryMatcher.group(1);
        }
        if (methodStart < 0 || methodName == null) {
            return null;
        }
        Matcher modelMatcher = MODEL_PATTERN.matcher(snippet.substring(0, methodStart));
        String rawModelType = null;
        while (modelMatcher.find()) {
            rawModelType = modelMatcher.group(1);
        }
        if (rawModelType == null || rawModelType.isBlank()) {
            rawModelType = resolveModelTypeFromTerminalCall(text, literalStartOffset, windowStart + methodStart);
        }
        if (rawModelType == null || rawModelType.isBlank()) {
            return null;
        }
        return new QueryContext(baseTypeName(rawModelType), methodName);
    }

    private static @Nullable String resolveModelTypeFromTerminalCall(String text, int literalStartOffset, int queryMethodOffset) {
        int searchEnd = Math.min(text.length(), literalStartOffset + 800);
        String tail = text.substring(literalStartOffset, searchEnd);
        Matcher terminalMatcher = TERMINAL_QUERY_PATTERN.matcher(tail);
        if (!terminalMatcher.find()) {
            return null;
        }
        String args = terminalMatcher.group(2);
        if (args == null || args.isBlank()) {
            return null;
        }
        Matcher varMatcher = RESULT_VAR_PATTERN.matcher(args);
        if (!varMatcher.find()) {
            return null;
        }
        String token = varMatcher.group(1);
        if (token == null || token.isBlank()) {
            return null;
        }
        if (looksLikeTypeToken(token)) {
            return token;
        }
        String inferred = resolveVariableTypeBefore(text, token, queryMethodOffset);
        return inferred == null || inferred.isBlank() ? null : inferred;
    }

    private static boolean looksLikeTypeToken(String token) {
        if (token.contains(".")) {
            String suffix = token.substring(token.lastIndexOf('.') + 1);
            return !suffix.isBlank() && Character.isUpperCase(suffix.charAt(0));
        }
        return Character.isUpperCase(token.charAt(0));
    }

    private static @Nullable String resolveVariableTypeBefore(String text, String variableName, int offset) {
        int searchStart = Math.max(0, offset - 1500);
        String scope = text.substring(searchStart, Math.max(searchStart, offset));
        String typeFromVar = findLastGroup(scope, "\\bvar\\s+" + Pattern.quote(variableName) + "\\s+([^\\n=]+)");
        if (typeFromVar != null && !typeFromVar.isBlank()) {
            return cleanTypeToken(typeFromVar);
        }
        String typeFromShortDecl = findLastGroup(scope, "\\b" + Pattern.quote(variableName) + "\\s*:=\\s*&?([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\{");
        if (typeFromShortDecl != null && !typeFromShortDecl.isBlank()) {
            return cleanTypeToken(typeFromShortDecl);
        }
        return null;
    }

    private static @Nullable String findLastGroup(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        String result = null;
        while (matcher.find()) {
            result = matcher.group(1);
        }
        return result == null ? null : result.trim();
    }

    private static @NotNull String cleanTypeToken(String typeToken) {
        String token = typeToken.trim();
        int space = token.indexOf(' ');
        if (space > 0) {
            token = token.substring(0, space);
        }
        int comma = token.indexOf(',');
        if (comma > 0) {
            token = token.substring(0, comma);
        }
        return token.trim();
    }

    private static List<TextRange> findColumnRanges(String sql, String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return List.of();
        }
        Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])(?:`?[A-Za-z_][A-Za-z0-9_]*`?\\.)?`?(" + Pattern.quote(columnName) + ")`?(?![A-Za-z0-9_])");
        Matcher matcher = pattern.matcher(sql);
        List<TextRange> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(TextRange.from(matcher.start(1), matcher.end(1) - matcher.start(1)));
        }
        return ranges;
    }

    private static @Nullable String extractGormColumn(String tagContent) {
        Matcher gormMatcher = GORM_TAG_VALUE_PATTERN.matcher(tagContent);
        if (!gormMatcher.find()) {
            return null;
        }
        String gormValue = gormMatcher.group(2);
        if (gormValue == null || gormValue.isBlank()) {
            return null;
        }
        Matcher columnMatcher = GORM_COLUMN_DIRECTIVE_PATTERN.matcher(gormValue);
        return columnMatcher.find() ? columnMatcher.group(2) : null;
    }

    private static int findLastWhitespace(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static @NotNull String baseTypeName(String typeName) {
        String current = typeName.trim();
        while (current.startsWith("[]")) {
            current = current.substring(2);
        }
        while (current.startsWith("*")) {
            current = current.substring(1);
        }
        int lastDot = current.lastIndexOf('.');
        if (lastDot >= 0) {
            current = current.substring(lastDot + 1);
        }
        int brace = current.indexOf('{');
        if (brace >= 0) {
            current = current.substring(0, brace);
        }
        return current.trim();
    }

    private static PsiElement findAnchor(PsiFile psiFile, int offset) {
        int safeOffset = Math.max(0, Math.min(offset, Math.max(0, psiFile.getTextLength() - 1)));
        PsiElement element = psiFile.findElementAt(safeOffset);
        return element != null ? element : psiFile;
    }

    private static boolean isSameAnchor(PsiElement anchor, PsiElement element) {
        if (anchor == null || element == null) {
            return false;
        }
        if (anchor.equals(element)) {
            return true;
        }
        if (anchor.getContainingFile() == null || element.getContainingFile() == null || !anchor.getContainingFile().equals(element.getContainingFile())) {
            return false;
        }
        TextRange left = anchor.getTextRange();
        TextRange right = element.getTextRange();
        return left != null
                && right != null
                && left.getStartOffset() == right.getStartOffset()
                && left.getEndOffset() == right.getEndOffset();
    }

    private static @Nullable PsiElement findElementAtRangeStart(PsiElement stringLiteral, TextRange rangeInElement) {
        if (stringLiteral.getContainingFile() == null || stringLiteral.getTextRange() == null) {
            return stringLiteral;
        }
        int absoluteOffset = stringLiteral.getTextRange().getStartOffset() + rangeInElement.getStartOffset();
        PsiElement element = stringLiteral.getContainingFile().findElementAt(absoluteOffset);
        return element != null ? element : stringLiteral;
    }

    private static TextRange shiftRange(TextRange range, int delta) {
        return TextRange.create(range.getStartOffset() + delta, range.getEndOffset() + delta);
    }

    private static String identity(PsiElement element) {
        PsiFile file = element.getContainingFile();
        TextRange range = element.getTextRange();
        String fileName = file == null || file.getVirtualFile() == null ? "<unknown>" : file.getVirtualFile().getPath();
        String offsets = range == null ? "0:0" : range.getStartOffset() + ":" + range.getEndOffset();
        return fileName + ":" + offsets;
    }

    private static String key(String structName, String columnName) {
        return structName + "#" + columnName.toLowerCase(Locale.ROOT);
    }

    private static final class QueryContext {
        private final String structName;
        private final String methodName;

        private QueryContext(String structName, String methodName) {
            this.structName = structName;
            this.methodName = methodName;
        }
    }

    private static final class Model {
        private final List<GoSpringGormFieldDefinition> allFields = new ArrayList<>();
        private final Map<String, List<GoSpringGormFieldDefinition>> ownFieldsByStruct = new LinkedHashMap<>();
        private final Map<String, List<String>> embeddedStructsByStruct = new LinkedHashMap<>();
        private final Map<String, List<GoSpringGormFieldDefinition>> fieldsByStruct = new LinkedHashMap<>();
        private final Map<String, GoSpringGormFieldDefinition> fieldByStructAndColumn = new LinkedHashMap<>();
        private final Map<String, List<GoSpringGormQueryUsage>> queryUsagesByLiteral = new LinkedHashMap<>();
        private final Map<String, List<GoSpringGormQueryUsage>> usagesByFieldKey = new LinkedHashMap<>();
    }
}
