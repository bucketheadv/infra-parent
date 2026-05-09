package io.infra.idea.plugin.gospring.navigation;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import io.infra.idea.plugin.gospring.metadata.GoSpringConfigMetadataSupport;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * infra-go {@code applog}：{@code applog.yaml} 中键与 {@code applog} 包内源码位置的对应（跳转、补全）。
 */
public final class InfraGoApplogYamlNavigation {
    private static final Map<String, String> LOGGER_KEY_TO_CONST;
    private static final Map<String, String> CONST_TO_FILE;

    /** {@link #LOGGER_KEY_TO_CONST} 的逆：{@code NameApp} → {@code app}。 */
    private static final Map<String, String> LOGGER_CONST_TO_YAML_KEY;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("app", "NameApp");
        m.put("access", "NameAccess");
        m.put("gorm", "NameGorm");
        m.put("gin", "NameGinWriter");
        m.put("root", "NameRoot");
        LOGGER_KEY_TO_CONST = Map.copyOf(m);

        Map<String, String> f = new LinkedHashMap<>();
        f.put("NameGorm", "gorm.go");
        f.put("NameGinWriter", "gin.go");
        CONST_TO_FILE = Map.copyOf(f);

        Map<String, String> inv = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : LOGGER_KEY_TO_CONST.entrySet()) {
            inv.put(e.getValue(), e.getKey());
        }
        LOGGER_CONST_TO_YAML_KEY = Map.copyOf(inv);
    }

    private static final Pattern YAML_STRUCT_FIELD_TAG = Pattern.compile("yaml:\"([^\"]+)\"");

    private static final Pattern GO_TYPE_STRUCT_DECL = Pattern.compile("(?m)^type\\s+(\\w+)\\s+struct\\s*\\{");

    private static final List<String> APPENDER_FIELD_KEYS = List.of(
            "type",
            "colored",
            "layout",
            "pattern",
            "path",
            "maxLinesPerFile",
            "retentionDays",
            "levelColors",
            "fieldColors"
    );

    private static final List<String> LOGGER_DEF_FIELD_KEYS = List.of("level", "appenders");

    private static final List<String> LOG_LEVEL_VALUES = List.of(
            "trace", "debug", "info", "warn", "error", "fatal"
    );

    private static final List<String> APPENDER_TYPE_VALUES = List.of("console", "rollingFile");

    private static final List<String> LAYOUT_VALUES = List.of("text", "pattern", "json");

    private static final List<String> BOOLEAN_YAML_VALUES = List.of("true", "false");

    private InfraGoApplogYamlNavigation() {
    }

    public static boolean isApplogYamlFile(@Nullable PsiFile file) {
        return GoSpringPsi.isApplogConfigFile(file);
    }

    /**
     * 从 infra-go {@code applog} 包内源码跳转到工程中的 {@code applog} YAML 对应键（与 {@link #findTargets} 相反方向）。
     *
     * @param offset 编辑器中的文档偏移（与 {@link com.intellij.openapi.editor.Editor#getCaretModel()} 一致）
     */
    public static PsiElement @Nullable [] findYamlTargetsFromGo(@NotNull Project project,
                                                                @NotNull PsiElement sourceElement,
                                                                int offset) {
        PsiFile file = sourceElement.getContainingFile();
        if (!GoSpringPsi.isGoFile(sourceElement) || file == null || file.getVirtualFile() == null) {
            return null;
        }
        if (!isApplogGoPackageFile(file)) {
            return null;
        }

        if ("config.go".equals(file.getName())) {
            String text = file.getText();
            if (text != null && yamlTagContainsOffset(text, offset)) {
                String tag = yamlTagValueAtOffset(text, offset);
                if (tag != null && !tag.isBlank()) {
                    PsiElement[] fromTag = resolveApplogYamlTagFromGoOffset(project, file, offset, tag);
                    if (fromTag != null && fromTag.length > 0) {
                        return fromTag;
                    }
                }
            }
        }

        String constName = resolveGoIdentifierAtOffset(file, offset);
        if (constName != null) {
            String loggerYamlKey = LOGGER_CONST_TO_YAML_KEY.get(constName);
            if (loggerYamlKey != null) {
                return findYamlTargetsForNamedLoggerConst(project, loggerYamlKey);
            }
        }
        return null;
    }

    /** {@code package applog} 的 Go 源文件（不依赖目录名或本地 {@code logger.go} 探测）。 */
    public static boolean isApplogGoPackageFile(@Nullable PsiFile file) {
        return file != null && GoSpringPsi.isGoFile(file) && declaresPackageApplog(file);
    }

    /**
     * {@code config.go} 中 {@code yaml:"..."} 标签（配合文件内偏移解析所在 struct）→ YAML 键。
     */
    public static PsiElement @Nullable [] resolveApplogYamlTagFromGoOffset(@NotNull Project project,
                                                                           @NotNull PsiFile goFile,
                                                                           int offsetInFile,
                                                                           @NotNull String yamlTag) {
        if (!isApplogGoPackageFile(goFile) || !"config.go".equals(goFile.getName())) {
            return null;
        }
        String text = goFile.getText();
        if (text == null) {
            return null;
        }
        String structName = detectStructNameAtOffset(text, offsetInFile);
        if (structName == null) {
            return null;
        }
        return collectYamlKeysForApplogStructField(project, structName, yamlTag);
    }

    /** 命名 logger 常量对应的 YAML 路径 {@code loggers.<yamlKey>}。 */
    public static PsiElement @Nullable [] findYamlTargetsForNamedLoggerConst(@NotNull Project project,
                                                                             @NotNull String loggerYamlKey) {
        if (loggerYamlKey.isBlank()) {
            return null;
        }
        return collectYamlKeysByPaths(project, List.of("loggers." + loggerYamlKey));
    }

    /** {@link #LOGGER_CONST_TO_YAML_KEY} 查找，未知常量返回 {@code null}。 */
    public static @Nullable String loggerYamlKeyForLoggerConst(@Nullable String constToken) {
        if (constToken == null) {
            return null;
        }
        return LOGGER_CONST_TO_YAML_KEY.get(constToken);
    }

    private static boolean yamlTagContainsOffset(@NotNull String text, int offset) {
        Matcher m = YAML_STRUCT_FIELD_TAG.matcher(text);
        while (m.find()) {
            if (offset >= m.start() && offset <= m.end()) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String yamlTagValueAtOffset(@NotNull String text, int offset) {
        Matcher m = YAML_STRUCT_FIELD_TAG.matcher(text);
        while (m.find()) {
            if (offset >= m.start() && offset <= m.end()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static @Nullable String detectStructNameAtOffset(@NotNull String text, int offset) {
        Matcher m = GO_TYPE_STRUCT_DECL.matcher(text);
        while (m.find()) {
            int open = text.indexOf('{', m.end() - 1);
            if (open < 0) {
                continue;
            }
            int close = findMatchingBrace(text, open);
            if (close > open && offset > open && offset < close) {
                return m.group(1);
            }
        }
        return null;
    }

    private static int findMatchingBrace(@NotNull String text, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static @Nullable String resolveGoIdentifierAtOffset(@NotNull PsiFile file, int offset) {
        PsiElement leaf = file.findElementAt(offset);
        if (leaf == null) {
            return null;
        }
        PsiNamedElement named = PsiTreeUtil.getParentOfType(leaf, PsiNamedElement.class, false);
        if (named != null && named.getName() != null) {
            TextRange tr = named.getTextRange();
            if (tr != null && tr.containsOffset(offset)) {
                return named.getName();
            }
        }
        PsiElement p = leaf;
        while (p != null && !(p instanceof PsiFile)) {
            String t = p.getText();
            TextRange tr = p.getTextRange();
            if (t != null && tr != null && tr.containsOffset(offset) && t.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return t;
            }
            p = p.getParent();
        }
        return null;
    }

    private static PsiElement @Nullable [] collectYamlKeysForApplogStructField(@NotNull Project project,
                                                                              @NotNull String structName,
                                                                              @NotNull String yamlTag) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        switch (structName) {
            case "yamlRoot":
                paths.add(yamlTag);
                break;
            case "yamlLoggerDef":
                for (YAMLFile yaml : loadApplogYamlPsiFiles(project)) {
                    YAMLMapping rootMap = topLevelMapping(yaml);
                    if (rootMap != null && findMappingEntry(rootMap, "root") != null) {
                        paths.add("root." + yamlTag);
                    }
                    for (String id : collectLoggerIdsUnderLoggers(yaml)) {
                        paths.add("loggers." + id + "." + yamlTag);
                    }
                }
                break;
            case "yamlAppender":
                for (YAMLFile yaml : loadApplogYamlPsiFiles(project)) {
                    for (String name : collectTopLevelAppenderDefinitionNames(yaml)) {
                        paths.add("appenders." + name + "." + yamlTag);
                    }
                }
                break;
            default:
                return null;
        }
        return collectYamlKeysByPaths(project, paths);
    }

    private static @Nullable YAMLMapping topLevelMapping(@NotNull YAMLFile yamlFile) {
        for (YAMLDocument doc : yamlFile.getDocuments()) {
            YAMLValue top = doc.getTopLevelValue();
            if (top instanceof YAMLMapping mapping) {
                return mapping;
            }
        }
        return null;
    }

    private static @NotNull List<String> collectLoggerIdsUnderLoggers(@NotNull YAMLFile yamlFile) {
        List<String> out = new ArrayList<>();
        YAMLMapping rootMap = topLevelMapping(yamlFile);
        if (rootMap == null) {
            return out;
        }
        YAMLKeyValue loggersKv = findMappingEntry(rootMap, "loggers");
        if (loggersKv == null || !(loggersKv.getValue() instanceof YAMLMapping lm)) {
            return out;
        }
        for (YAMLKeyValue kv : lm.getKeyValues()) {
            String k = kv.getKeyText();
            if (k != null && !k.isBlank()) {
                out.add(k);
            }
        }
        return out;
    }

    private static @NotNull List<YAMLFile> loadApplogYamlPsiFiles(@NotNull Project project) {
        List<YAMLFile> out = new ArrayList<>();
        PsiManager pm = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Set<VirtualFile> seen = new LinkedHashSet<>();
        for (String fname : List.of("applog.yaml", "applog.yml")) {
            for (VirtualFile vf : FilenameIndex.getVirtualFilesByName(project, fname, scope)) {
                if (!seen.add(vf)) {
                    continue;
                }
                PsiFile pf = pm.findFile(vf);
                if (pf instanceof YAMLFile y && GoSpringPsi.isApplogConfigFile(pf)) {
                    out.add(y);
                }
            }
        }
        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "yaml", scope)) {
            addApplogYamlIfMatching(pm, vf, seen, out);
        }
        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "yml", scope)) {
            addApplogYamlIfMatching(pm, vf, seen, out);
        }
        return out;
    }

    private static void addApplogYamlIfMatching(@NotNull PsiManager pm,
                                                @NotNull VirtualFile vf,
                                                @NotNull Set<VirtualFile> seen,
                                                @NotNull List<YAMLFile> out) {
        if (!seen.add(vf)) {
            return;
        }
        String n = vf.getName().toLowerCase(Locale.ROOT);
        if (!n.contains("applog")) {
            return;
        }
        PsiFile pf = pm.findFile(vf);
        if (pf instanceof YAMLFile y && GoSpringPsi.isApplogConfigFile(pf)) {
            out.add(y);
        }
    }

    private static PsiElement @Nullable [] collectYamlKeysByPaths(@NotNull Project project,
                                                                  @NotNull Collection<String> paths) {
        if (paths.isEmpty()) {
            return null;
        }
        LinkedHashSet<PsiElement> targets = new LinkedHashSet<>();
        LinkedHashSet<String> uniq = new LinkedHashSet<>(paths);
        for (YAMLFile yaml : loadApplogYamlPsiFiles(project)) {
            for (String path : uniq) {
                YAMLKeyValue kv = findKeyValueByPropertyPath(yaml, path);
                if (kv != null) {
                    PsiElement keyPsi = kv.getKey();
                    targets.add(GoSpringNavigationTargetElement.wrap(keyPsi != null ? keyPsi : kv));
                }
            }
        }
        return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
    }

    private static @Nullable YAMLKeyValue findKeyValueByPropertyPath(@NotNull YAMLFile yamlFile,
                                                                   @NotNull String propertyPath) {
        if (propertyPath.isBlank()) {
            return null;
        }
        String[] segments = propertyPath.split("\\.");
        YAMLMapping current = topLevelMapping(yamlFile);
        if (current == null) {
            return null;
        }
        YAMLKeyValue lastKv = null;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isBlank()) {
                return null;
            }
            lastKv = findMappingEntry(current, seg);
            if (lastKv == null) {
                return null;
            }
            if (i == segments.length - 1) {
                return lastKv;
            }
            YAMLValue v = lastKv.getValue();
            if (!(v instanceof YAMLMapping nested)) {
                return null;
            }
            current = nested;
        }
        return lastKv;
    }

    /**
     * @param propertyKey {@link GoSpringConfigKeyNavigationSupport#buildYamlPropertyKey}
     */
    public static PsiElement @Nullable [] findTargets(@NotNull Project project, @NotNull String propertyKey) {
        return findTargets(project, propertyKey, null, null, -1);
    }

    public static PsiElement @Nullable [] findTargets(@NotNull Project project,
                                                        @NotNull String propertyKey,
                                                        @Nullable PsiFile yamlFile,
                                                        @Nullable PsiElement yamlSourceElement,
                                                        int yamlFileOffset) {
        if (yamlFile instanceof YAMLFile applogYaml && isApplogYamlFile(yamlFile)) {
            if (yamlSourceElement != null && yamlFileOffset >= 0) {
                PsiElement[] listToDef = resolveAppenderListItemToTopLevelDefinition(applogYaml, yamlSourceElement, yamlFileOffset);
                if (listToDef != null) {
                    return listToDef;
                }
                PsiElement[] defToUses = resolveTopLevelAppenderKeyToUsages(applogYaml, yamlSourceElement, yamlFileOffset, propertyKey);
                if (defToUses != null) {
                    return defToUses;
                }
            }
            if (yamlFileOffset < 0) {
                PsiElement[] gutter = resolveAppenderDefinitionToUsagesForGutter(applogYaml, propertyKey);
                if (gutter != null) {
                    return gutter;
                }
            }
        }

        PsiElement[] loggers = findLoggerConstTargets(project, propertyKey);
        if (loggers != null) {
            return loggers;
        }
        PsiElement[] appenders = findAppenderSchemaTargets(project, propertyKey);
        if (appenders != null) {
            return appenders;
        }
        PsiElement[] misc = findMiscConfigTargets(project, propertyKey);
        if (misc != null) {
            return misc;
        }
        return null;
    }

    private static PsiElement @Nullable [] resolveAppenderListItemToTopLevelDefinition(@NotNull YAMLFile yamlFile,
                                                                                       @NotNull PsiElement yamlSourceElement,
                                                                                       int yamlFileOffset) {
        PsiElement leaf = yamlFile.findElementAt(yamlFileOffset);
        if (leaf == null) {
            leaf = yamlSourceElement;
        }
        YAMLSequenceItem item = PsiTreeUtil.getParentOfType(leaf, YAMLSequenceItem.class, false);
        if (item == null) {
            return null;
        }
        YAMLSequence sequence = PsiTreeUtil.getParentOfType(item, YAMLSequence.class, true);
        if (sequence == null) {
            return null;
        }
        PsiElement seqParent = sequence.getParent();
        if (!(seqParent instanceof YAMLKeyValue appendersKv)) {
            return null;
        }
        if (!"appenders".equals(appendersKv.getKeyText())) {
            return null;
        }
        String path = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(appendersKv);
        if (!isLoggerOrRootAppendersSequencePath(path)) {
            return null;
        }
        String name = sequenceItemText(item);
        if (name == null || name.isBlank()) {
            return null;
        }
        PsiElement defKey = findTopLevelAppenderDefinitionKey(yamlFile, name);
        if (defKey == null) {
            return null;
        }
        return new PsiElement[]{GoSpringNavigationTargetElement.wrap(defKey)};
    }

    private static PsiElement @Nullable [] resolveTopLevelAppenderKeyToUsages(@NotNull YAMLFile yamlFile,
                                                                              @NotNull PsiElement yamlSourceElement,
                                                                              int yamlFileOffset,
                                                                              @NotNull String propertyKey) {
        if (!propertyKey.startsWith("appenders.")) {
            return null;
        }
        String after = propertyKey.substring("appenders.".length());
        if (after.isBlank() || after.indexOf('.') >= 0) {
            return null;
        }
        YAMLKeyValue owner = PsiTreeUtil.getParentOfType(yamlSourceElement, YAMLKeyValue.class, false);
        if (owner == null) {
            return null;
        }
        String ownerPath = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(owner);
        if (!propertyKey.equals(ownerPath)) {
            return null;
        }
        if (!isTopLevelAppenderDefinitionKey(owner)) {
            return null;
        }
        PsiElement keyPsi = owner.getKey();
        if (keyPsi == null || keyPsi.getTextRange() == null || !keyPsi.getTextRange().containsOffset(yamlFileOffset)) {
            return null;
        }
        List<PsiElement> uses = collectAppenderNameReferencesInLoggerOrRootLists(yamlFile, after);
        if (uses.isEmpty()) {
            return null;
        }
        PsiElement[] out = new PsiElement[uses.size()];
        for (int i = 0; i < uses.size(); i++) {
            out[i] = GoSpringNavigationTargetElement.wrap(uses.get(i));
        }
        return out;
    }

    private static PsiElement @Nullable [] resolveAppenderDefinitionToUsagesForGutter(@NotNull YAMLFile yamlFile,
                                                                                       @NotNull String propertyKey) {
        if (!propertyKey.startsWith("appenders.")) {
            return null;
        }
        String after = propertyKey.substring("appenders.".length());
        if (after.isBlank() || after.indexOf('.') >= 0) {
            return null;
        }
        List<PsiElement> uses = collectAppenderNameReferencesInLoggerOrRootLists(yamlFile, after);
        if (uses.isEmpty()) {
            return null;
        }
        PsiElement[] out = new PsiElement[uses.size()];
        for (int i = 0; i < uses.size(); i++) {
            out[i] = GoSpringNavigationTargetElement.wrap(uses.get(i));
        }
        return out;
    }

    private static boolean isLoggerOrRootAppendersSequencePath(@Nullable String path) {
        if (path == null) {
            return false;
        }
        if ("root.appenders".equals(path)) {
            return true;
        }
        if (!path.startsWith("loggers.") || !path.endsWith(".appenders")) {
            return false;
        }
        String middle = path.substring("loggers.".length(), path.length() - ".appenders".length());
        return !middle.isEmpty() && middle.indexOf('.') < 0;
    }

    private static boolean isTopLevelAppenderDefinitionKey(@NotNull YAMLKeyValue appenderNameKv) {
        PsiElement parent = appenderNameKv.getParent();
        if (!(parent instanceof YAMLMapping)) {
            return false;
        }
        YAMLKeyValue appendersKv = PsiTreeUtil.getParentOfType(parent, YAMLKeyValue.class, false);
        if (appendersKv == null || !"appenders".equals(appendersKv.getKeyText())) {
            return false;
        }
        PsiElement rootMap = appendersKv.getParent();
        while (rootMap != null && !(rootMap instanceof YAMLMapping)) {
            rootMap = rootMap.getParent();
        }
        if (!(rootMap instanceof YAMLMapping)) {
            return false;
        }
        return rootMap.getParent() instanceof YAMLDocument;
    }

    private static @Nullable PsiElement findTopLevelAppenderDefinitionKey(@NotNull YAMLFile yamlFile, @NotNull String appenderName) {
        for (YAMLDocument doc : yamlFile.getDocuments()) {
            YAMLValue top = doc.getTopLevelValue();
            if (!(top instanceof YAMLMapping root)) {
                continue;
            }
            YAMLKeyValue appendersKv = findMappingEntry(root, "appenders");
            if (appendersKv == null) {
                continue;
            }
            YAMLValue val = appendersKv.getValue();
            if (!(val instanceof YAMLMapping am)) {
                continue;
            }
            YAMLKeyValue child = findMappingEntry(am, appenderName);
            if (child == null) {
                continue;
            }
            PsiElement key = child.getKey();
            return key != null ? key : child;
        }
        return null;
    }

    private static @Nullable YAMLKeyValue findMappingEntry(@NotNull YAMLMapping mapping, @NotNull String key) {
        for (YAMLKeyValue kv : mapping.getKeyValues()) {
            if (key.equals(kv.getKeyText())) {
                return kv;
            }
        }
        return null;
    }

    private static @Nullable String sequenceItemText(@NotNull YAMLSequenceItem item) {
        YAMLValue v = item.getValue();
        if (v instanceof YAMLScalar sc) {
            String s = sc.getTextValue();
            return s != null ? s.trim() : null;
        }
        return null;
    }

    private static @NotNull List<PsiElement> collectAppenderNameReferencesInLoggerOrRootLists(@NotNull YAMLFile yamlFile,
                                                                                              @NotNull String appenderName) {
        List<PsiElement> out = new ArrayList<>();
        for (YAMLKeyValue kv : PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue.class)) {
            if (!"appenders".equals(kv.getKeyText())) {
                continue;
            }
            String path = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(kv);
            if (!isLoggerOrRootAppendersSequencePath(path)) {
                continue;
            }
            YAMLValue v = kv.getValue();
            if (!(v instanceof YAMLSequence seq)) {
                continue;
            }
            for (YAMLSequenceItem item : seq.getItems()) {
                String t = sequenceItemText(item);
                if (appenderName.equals(t)) {
                    YAMLValue val = item.getValue();
                    if (val != null) {
                        out.add(val);
                    }
                }
            }
        }
        return out;
    }

    private static PsiElement @Nullable [] findLoggerConstTargets(@NotNull Project project, @NotNull String propertyKey) {
        String loggerYamlKey = extractFirstSegmentAfterPrefix(propertyKey, "loggers.");
        if (loggerYamlKey == null || loggerYamlKey.isBlank()) {
            return null;
        }
        if (!propertyKey.equals("loggers." + loggerYamlKey)) {
            return null;
        }
        String constName = LOGGER_KEY_TO_CONST.get(loggerYamlKey);
        if (constName == null) {
            return null;
        }
        VirtualFile applogDir = findApplogPackageDir(project);
        if (applogDir == null) {
            return null;
        }
        String goFileName = CONST_TO_FILE.getOrDefault(constName, "logger.go");
        VirtualFile goVf = applogDir.findChild(goFileName);
        if (goVf == null) {
            return null;
        }
        PsiFile goFile = PsiManager.getInstance(project).findFile(goVf);
        if (goFile == null) {
            return null;
        }
        PsiElement id = findConstIdentifier(goFile, constName);
        if (id == null) {
            return null;
        }
        return new PsiElement[]{GoSpringNavigationTargetElement.wrap(id)};
    }

    /**
     * {@code appenders} / {@code appenders.*} → {@code config.go} 中 {@code yamlRoot}、{@code yamlAppender} 或 {@code buildAppenders}。
     */
    private static PsiElement @Nullable [] findAppenderSchemaTargets(@NotNull Project project, @NotNull String propertyKey) {
        PsiFile configGo = findApplogConfigGo(project);
        if (configGo == null) {
            return null;
        }
        if ("appenders".equals(propertyKey)) {
            return wrapGoAnchor(configGo, "Appenders map[string]yamlAppender", "yaml:\"appenders\"");
        }
        if (propertyKey.startsWith("appenders.")) {
            String rest = propertyKey.substring("appenders.".length());
            if (rest.isBlank()) {
                return null;
            }
            int dot = rest.indexOf('.');
            String subKey = dot < 0 ? "" : rest.substring(dot + 1);
            if ("type".equals(subKey)) {
                PsiElement[] sw = wrapGoAnchor(configGo, "switch a.Type");
                if (sw != null) {
                    return sw;
                }
            }
            if (!subKey.isBlank()) {
                PsiElement[] field = findYamlAppenderFieldAnchor(configGo, subKey);
                if (field != null) {
                    return field;
                }
            }
            return wrapGoAnchor(configGo, "type yamlAppender struct");
        }
        return null;
    }

    private static PsiElement @Nullable [] findMiscConfigTargets(@NotNull Project project, @NotNull String propertyKey) {
        PsiFile configGo = findApplogConfigGo(project);
        if (configGo == null) {
            return null;
        }
        if ("loggers".equals(propertyKey)) {
            return wrapGoAnchor(configGo, "Loggers map[string]yamlLoggerDef", "yaml:\"loggers\"");
        }
        if ("root".equals(propertyKey) || propertyKey.startsWith("root.")) {
            return wrapGoAnchor(configGo, "type yamlLoggerDef struct");
        }
        if ("callerFileMaxLen".equals(propertyKey) || propertyKey.endsWith(".callerFileMaxLen")) {
            return wrapGoAnchor(configGo, "CallerFileMaxLen", "yaml:\"callerFileMaxLen\"");
        }
        return null;
    }

    private static PsiElement @Nullable [] wrapGoAnchor(@NotNull PsiFile file, @NotNull String primaryNeedle, String @Nullable ... fallbacks) {
        PsiElement el = findGoAnchorElement(file, primaryNeedle);
        if (el == null && fallbacks != null) {
            for (String fb : fallbacks) {
                if (fb == null) {
                    continue;
                }
                el = findGoAnchorElement(file, fb);
                if (el != null) {
                    break;
                }
            }
        }
        if (el == null) {
            return null;
        }
        return new PsiElement[]{GoSpringNavigationTargetElement.wrap(el)};
    }

    private static @Nullable PsiElement findGoAnchorElement(@NotNull PsiFile file, @NotNull String needle) {
        String text = file.getText();
        if (text == null) {
            return null;
        }
        int idx = text.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int anchor = idx + needle.length() / 2;
        PsiElement at = file.findElementAt(anchor);
        if (at != null) {
            return at;
        }
        return file.findElementAt(idx);
    }

    /**
     * {@code appenders.&lt;name&gt;.&lt;field&gt;} → {@code yamlAppender} 中带 {@code yaml:"field"} 的字段行（支持 {@code a.b} 回退到 {@code yaml:"a"}）。
     */
    private static PsiElement @Nullable [] findYamlAppenderFieldAnchor(@NotNull PsiFile configGo, @NotNull String subKeyTail) {
        for (String needle : yamlTagNeedleCandidates(subKeyTail)) {
            PsiElement el = findGoAnchorElement(configGo, needle);
            if (el != null) {
                return new PsiElement[]{GoSpringNavigationTargetElement.wrap(el)};
            }
        }
        String firstSeg = subKeyTail;
        int dot = subKeyTail.indexOf('.');
        if (dot > 0) {
            firstSeg = subKeyTail.substring(0, dot);
        }
        String exported = toExportedGoFieldName(firstSeg);
        if (!exported.isBlank()) {
            PsiElement el = findGoAnchorElement(configGo, "\t" + exported + "\t");
            if (el == null) {
                el = findGoAnchorElement(configGo, "\t" + exported + " ");
            }
            if (el != null) {
                return new PsiElement[]{GoSpringNavigationTargetElement.wrap(el)};
            }
        }
        return null;
    }

    private static @NotNull List<String> yamlTagNeedleCandidates(@NotNull String subKeyTail) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        String s = subKeyTail;
        while (!s.isBlank()) {
            ordered.add("yaml:\"" + s + "\"");
            int lastDot = s.lastIndexOf('.');
            if (lastDot < 0) {
                break;
            }
            s = s.substring(0, lastDot);
        }
        return new ArrayList<>(ordered);
    }

    private static @NotNull String toExportedGoFieldName(@NotNull String yamlFieldKey) {
        if (yamlFieldKey.isEmpty()) {
            return yamlFieldKey;
        }
        char c = yamlFieldKey.charAt(0);
        if (Character.isUpperCase(c)) {
            return yamlFieldKey;
        }
        return Character.toUpperCase(c) + yamlFieldKey.substring(1);
    }

    private static @Nullable PsiFile findApplogConfigGo(@NotNull Project project) {
        VirtualFile dir = findApplogPackageDir(project);
        if (dir == null) {
            return null;
        }
        VirtualFile vf = dir.findChild("config.go");
        if (vf == null) {
            return null;
        }
        return PsiManager.getInstance(project).findFile(vf);
    }

    /**
     * 在 {@code applog.yaml} 中由代码补全（Ctrl+Space）调用：优先于 Spring configuration-metadata。
     */
    public static boolean addApplogYamlCompletionsFromContext(@NotNull PsiFile file,
                                                              @NotNull GoSpringConfigMetadataSupport.YamlCompletionContext context,
                                                              @NotNull CompletionResultSet result) {
        if (!isApplogYamlFile(file) || !(file instanceof YAMLFile yamlFile)) {
            return false;
        }
        String parentKey = context.getParentKey();
        String typed = context.getTypedPrefix();
        if (context.isFlattenedKey() && typed != null && typed.contains(".")) {
            int ld = typed.lastIndexOf('.');
            parentKey = typed.substring(0, ld);
            typed = typed.substring(ld + 1);
        }
        String parent = parentKey == null ? "" : parentKey.trim();
        String normTyped = normalizeApplogYamlCompletionPrefix(typed);
        CompletionResultSet target = result.withPrefixMatcher(normTyped == null ? "" : normTyped);
        Set<String> existing = GoSpringConfigMetadataSupport.collectExistingYamlChildren(file, parent.isEmpty() ? null : parent);
        List<LookupElementBuilder> lookups = collectApplogKeyCompletionLookups(yamlFile, parent, existing);
        if (lookups.isEmpty()) {
            return false;
        }
        for (LookupElementBuilder b : lookups) {
            target.addElement(b);
        }
        return true;
    }

    /**
     * 标量值 / 列表项等位置的输入提示（Ctrl+Space）。
     * 请传入与编辑器一致的文档全文（{@code Document#getText()}），避免 PSI 未提交时与光标不同步。
     */
    public static boolean addApplogYamlValueCompletions(@NotNull PsiFile file,
                                                       @NotNull String documentText,
                                                       int offset,
                                                       @NotNull CompletionResultSet result) {
        if (!isApplogYamlFile(file)) {
            return false;
        }
        if (!(file instanceof YAMLFile yamlFile)) {
            return false;
        }
        int len = documentText.length();
        int safe;
        if (len <= 0) {
            safe = 0;
        } else if (offset >= len) {
            safe = len - 1;
        } else {
            safe = Math.max(0, offset);
        }
        PsiElement leaf = file.findElementAt(safe);
        if (leaf == null) {
            return false;
        }
        if (tryCompleteAppenderListItemValue(yamlFile, documentText, leaf, offset, result)) {
            return true;
        }
        YAMLKeyValue valueHost = findInnermostYamlKeyValueForValueEdit(leaf, offset);
        if (valueHost == null) {
            return false;
        }
        String fullKey = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(valueHost);
        if (fullKey == null || fullKey.isBlank()) {
            return false;
        }
        String prefix = extractYamlValuePrefixForCompletion(valueHost, documentText, offset);
        CompletionResultSet prefixed = result.withPrefixMatcher(prefix);
        String leafKey = lastPropertySegment(fullKey);
        List<LookupElementBuilder> lookups = switch (leafKey) {
            case "level" -> lookupsFromStrings(LOG_LEVEL_VALUES, "yamlLoggerDef", "日志级别");
            case "type" -> lookupsFromStrings(APPENDER_TYPE_VALUES, "yamlAppender", "console | rollingFile");
            case "layout" -> lookupsFromStrings(LAYOUT_VALUES, "yamlAppender", "text | pattern | json");
            case "colored" -> lookupsFromStrings(BOOLEAN_YAML_VALUES, "yamlAppender", "是否 ANSI 着色");
            case "maxLinesPerFile" -> numberHintLookups(List.of("100000", "50000", "10000"), "rollingFile", "单文件最大行数");
            case "retentionDays" -> numberHintLookups(List.of("7", "30", "90", "365"), "rollingFile", "历史保留天数");
            case "callerFileMaxLen" -> numberHintLookups(List.of("36", "48", "64"), "yamlRoot", "file:line 显示宽度");
            case "pattern" -> patternSnippetLookups();
            default -> List.of();
        };
        if (lookups.isEmpty()) {
            return false;
        }
        for (LookupElementBuilder b : lookups) {
            prefixed.addElement(b);
        }
        return true;
    }

    private static boolean tryCompleteAppenderListItemValue(@NotNull YAMLFile yamlFile,
                                                             @NotNull String fileText,
                                                             @NotNull PsiElement leaf,
                                                             int offset,
                                                             @NotNull CompletionResultSet result) {
        YAMLSequenceItem item = PsiTreeUtil.getParentOfType(leaf, YAMLSequenceItem.class, false);
        if (item == null || !isOffsetInAppenderReferenceListItem(item, offset)) {
            return false;
        }
        YAMLSequence seq = PsiTreeUtil.getParentOfType(item, YAMLSequence.class, false);
        YAMLKeyValue appendersKv = seq == null ? null : PsiTreeUtil.getParentOfType(seq, YAMLKeyValue.class, false);
        if (appendersKv == null || !"appenders".equals(appendersKv.getKeyText())) {
            return false;
        }
        String path = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(appendersKv);
        if (!isLoggerOrRootAppendersSequencePath(path)) {
            return false;
        }
        String prefix = extractSequenceItemScalarPrefix(item, fileText, offset);
        CompletionResultSet prefixed = result.withPrefixMatcher(prefix);
        Set<String> chosen = collectSequenceScalarTextsForParent(yamlFile, path);
        boolean any = false;
        for (String name : collectTopLevelAppenderDefinitionNames(yamlFile)) {
            if (!chosen.contains(name)) {
                prefixed.addElement(LookupElementBuilder.create(name)
                        .withTypeText("applog", true)
                        .withTailText("  顶层 appenders 名称", true));
                any = true;
            }
        }
        for (String sample : List.of("stdConsole", "appFile")) {
            if (!chosen.contains(sample)) {
                prefixed.addElement(LookupElementBuilder.create(sample)
                        .withTypeText("applog", true)
                        .withTailText("  示例名", true));
                any = true;
            }
        }
        return any;
    }

    private static boolean isOffsetInAppenderReferenceListItem(@NotNull YAMLSequenceItem item, int offset) {
        YAMLValue v = item.getValue();
        if (v != null) {
            if (v instanceof YAMLScalar) {
                return v.getTextRange().containsOffset(offset);
            }
            return false;
        }
        TextRange tr = item.getTextRange();
        return tr.containsOffset(offset);
    }

    private static @NotNull String extractSequenceItemScalarPrefix(@NotNull YAMLSequenceItem item,
                                                                   @NotNull String fileText,
                                                                   int offset) {
        YAMLValue v = item.getValue();
        if (v instanceof YAMLScalar sc) {
            TextRange tr = sc.getTextRange();
            if (offset >= tr.getStartOffset()) {
                return fileText.substring(tr.getStartOffset(), Math.min(offset, tr.getEndOffset()));
            }
        }
        int lineStart = fileText.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        String line = fileText.substring(lineStart, Math.min(offset, fileText.length()));
        line = line.replace("IntellijIdeaRulezzz", "");
        int dash = line.indexOf('-');
        if (dash >= 0) {
            line = trimLeadingWhitespace(line.substring(dash + 1));
        }
        return line.trim();
    }

    private static @NotNull String extractYamlValuePrefixForCompletion(@NotNull YAMLKeyValue kv,
                                                                       @NotNull String fileText,
                                                                       int offset) {
        YAMLValue val = kv.getValue();
        if (val != null && !(val instanceof YAMLMapping) && !(val instanceof YAMLSequence)) {
            TextRange tr = val.getTextRange();
            int start = tr.getStartOffset();
            if (offset >= start) {
                String chunk = fileText.substring(start, Math.min(offset, tr.getEndOffset()));
                return stripLeadingYamlQuotes(chunk.replace("IntellijIdeaRulezzz", "")).trim();
            }
        }
        int vs = findScalarValueStartOffset(kv);
        if (vs >= 0 && offset >= vs) {
            String chunk = fileText.substring(vs, Math.min(offset, kv.getTextRange().getEndOffset()))
                    .replace("IntellijIdeaRulezzz", "");
            return stripLeadingYamlQuotes(chunk).trim();
        }
        return "";
    }

    private static @NotNull String stripLeadingYamlQuotes(@NotNull String raw) {
        String s = raw.trim();
        while (!s.isEmpty() && (s.charAt(0) == '\'' || s.charAt(0) == '"')) {
            s = s.substring(1);
        }
        return s.trim();
    }

    private static @Nullable YAMLKeyValue findInnermostYamlKeyValueForValueEdit(@NotNull PsiElement leaf, int offset) {
        PsiElement p = leaf;
        while (p != null && !(p instanceof YAMLFile)) {
            if (p instanceof YAMLKeyValue kv && isCaretInScalarValueOfKeyValue(kv, offset)) {
                return kv;
            }
            p = p.getParent();
        }
        return null;
    }

    private static boolean isCaretInScalarValueOfKeyValue(@NotNull YAMLKeyValue kv, int offset) {
        PsiElement keyPsi = kv.getKey();
        if (keyPsi != null && keyPsi.getTextRange().containsOffset(offset)) {
            return false;
        }
        YAMLValue val = kv.getValue();
        if (val instanceof YAMLSequence || val instanceof YAMLMapping) {
            return false;
        }
        if (val instanceof YAMLScalar) {
            return val.getTextRange().containsOffset(offset);
        }
        if (val != null) {
            return val.getTextRange().containsOffset(offset);
        }
        int valueStart = findScalarValueStartOffset(kv);
        return valueStart >= 0
                && offset >= valueStart
                && offset <= kv.getTextRange().getEndOffset();
    }

    private static int findScalarValueStartOffset(@NotNull YAMLKeyValue kv) {
        PsiElement keyPsi = kv.getKey();
        if (keyPsi == null) {
            return -1;
        }
        PsiFile file = kv.getContainingFile();
        String text = file.getText();
        int i = keyPsi.getTextRange().getEndOffset();
        int end = kv.getTextRange().getEndOffset();
        while (i < end && i < text.length()) {
            char c = text.charAt(i);
            if (c == ':') {
                i++;
                while (i < end && i < text.length() && Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                return i;
            }
            if (c == '\n' || c == '\r') {
                break;
            }
            i++;
        }
        return -1;
    }

    private static @NotNull String lastPropertySegment(@NotNull String propertyKey) {
        int d = propertyKey.lastIndexOf('.');
        return d < 0 ? propertyKey : propertyKey.substring(d + 1);
    }

    private static @NotNull List<LookupElementBuilder> lookupsFromStrings(@NotNull List<String> values,
                                                                          @NotNull String typeText,
                                                                          @NotNull String tail) {
        List<LookupElementBuilder> out = new ArrayList<>();
        for (String v : values) {
            out.add(LookupElementBuilder.create(v)
                    .withTypeText(typeText, true)
                    .withTailText("  " + tail, true));
        }
        return out;
    }

    private static @NotNull List<LookupElementBuilder> numberHintLookups(@NotNull List<String> samples,
                                                                         @NotNull String typeText,
                                                                         @NotNull String tail) {
        List<LookupElementBuilder> out = new ArrayList<>();
        for (String v : samples) {
            out.add(LookupElementBuilder.create(v)
                    .withTypeText(typeText, true)
                    .withTailText("  " + tail, true));
        }
        return out;
    }

    private static @NotNull List<LookupElementBuilder> patternSnippetLookups() {
        return List.of(
                LookupElementBuilder.create("%d{yyyy-MM-dd HH:mm:ss.SSS} %level %fileLine %msg%n")
                        .withTypeText("pattern", true)
                        .withTailText("  示例 pattern", true),
                LookupElementBuilder.create("%clr(%level){cyan} %msg%n")
                        .withTypeText("pattern", true)
                        .withTailText("  clr 占位示例", true)
        );
    }

    public static @NotNull String normalizeApplogYamlCompletionPrefix(@Nullable String typedPrefix) {
        if (typedPrefix == null) {
            return "";
        }
        String t = typedPrefix.trim();
        if (t.startsWith("-")) {
            t = trimLeadingWhitespace(t.substring(1));
        }
        return t;
    }

    /**
     * 在 Spring 元数据为空时，为 YAML 键上的 {@code PsiReference#getVariants()} 提供补全。
     *
     * @param propertyKey 当前 YAML 键的完整路径（如 {@code loggers.app.level}）
     */
    public static Object @NotNull [] buildCompletionVariantsIfApplog(@Nullable PsiFile file, @NotNull String propertyKey) {
        if (!isApplogYamlFile(file) || !(file instanceof YAMLFile yamlFile)) {
            return new Object[0];
        }
        int lastDot = propertyKey.lastIndexOf('.');
        String parentKey = lastDot < 0 ? "" : propertyKey.substring(0, lastDot);
        Set<String> existing = GoSpringConfigMetadataSupport.collectExistingYamlChildren(
                file,
                parentKey.isEmpty() ? null : parentKey
        );
        return collectApplogKeyCompletionLookups(yamlFile, parentKey, existing).toArray();
    }

    private static @NotNull List<LookupElementBuilder> collectApplogKeyCompletionLookups(@NotNull YAMLFile yamlFile,
                                                                                           @NotNull String parentKey,
                                                                                           @NotNull Set<String> existing) {
        List<LookupElementBuilder> out = new ArrayList<>();
        if (parentKey.isEmpty()) {
            addLookupIfNew(out, existing, "callerFileMaxLen", "yamlRoot", "applog.yaml");
            addLookupIfNew(out, existing, "appenders", "具名 appender 表", "applog.yaml");
            addLookupIfNew(out, existing, "root", "根 logger", "applog.yaml");
            addLookupIfNew(out, existing, "loggers", "命名 logger 表", "applog.yaml");
            return out;
        }
        if ("appenders".equals(parentKey)) {
            LinkedHashSet<String> names = new LinkedHashSet<>(collectTopLevelAppenderDefinitionNames(yamlFile));
            names.add("stdConsole");
            names.add("appFile");
            for (String name : names) {
                if (!existing.contains(name)) {
                    out.add(LookupElementBuilder.create(name)
                            .withTypeText("applog", true)
                            .withTailText("  appender 名称", true));
                }
            }
            return out;
        }
        if (isAppenderDefinitionBlockParent(parentKey)) {
            for (String f : APPENDER_FIELD_KEYS) {
                if (!existing.contains(f)) {
                    out.add(LookupElementBuilder.create(f)
                            .withTypeText("yamlAppender", true)
                            .withTailText("  applog/config.go", true));
                }
            }
            return out;
        }
        if ("loggers".equals(parentKey)) {
            for (String k : LOGGER_KEY_TO_CONST.keySet()) {
                if (!existing.contains(k)) {
                    out.add(LookupElementBuilder.create(k)
                            .withTypeText("infra-go/applog", true)
                            .withTailText("  → " + LOGGER_KEY_TO_CONST.get(k), true));
                }
            }
            return out;
        }
        if (isLoggerDefinitionBlockParent(parentKey) || "root".equals(parentKey)) {
            for (String f : LOGGER_DEF_FIELD_KEYS) {
                if (!existing.contains(f)) {
                    out.add(LookupElementBuilder.create(f)
                            .withTypeText("yamlLoggerDef", true)
                            .withTailText("  applog/config.go", true));
                }
            }
            return out;
        }
        if (isLoggerOrRootAppendersSequencePath(parentKey)) {
            Set<String> already = new LinkedHashSet<>(existing);
            already.addAll(collectSequenceScalarTextsForParent(yamlFile, parentKey));
            for (String name : collectTopLevelAppenderDefinitionNames(yamlFile)) {
                if (!already.contains(name)) {
                    out.add(LookupElementBuilder.create(name)
                            .withTypeText("applog", true)
                            .withTailText("  引用顶层 appenders", true));
                }
            }
        }
        return out;
    }

    private static void addLookupIfNew(@NotNull List<LookupElementBuilder> out,
                                       @NotNull Set<String> existing,
                                       @NotNull String key,
                                       @NotNull String tail,
                                       @NotNull String typeText) {
        if (!existing.contains(key)) {
            out.add(LookupElementBuilder.create(key)
                    .withTypeText(typeText, true)
                    .withTailText("  " + tail, true));
        }
    }

    private static boolean isAppenderDefinitionBlockParent(@NotNull String parentKey) {
        if (!parentKey.startsWith("appenders.")) {
            return false;
        }
        String rest = parentKey.substring("appenders.".length());
        return !rest.isEmpty() && rest.indexOf('.') < 0;
    }

    private static boolean isLoggerDefinitionBlockParent(@NotNull String parentKey) {
        if (!parentKey.startsWith("loggers.")) {
            return false;
        }
        String rest = parentKey.substring("loggers.".length());
        return !rest.isEmpty() && rest.indexOf('.') < 0;
    }

    private static @NotNull List<String> collectTopLevelAppenderDefinitionNames(@NotNull YAMLFile yamlFile) {
        List<String> names = new ArrayList<>();
        for (YAMLDocument doc : yamlFile.getDocuments()) {
            YAMLValue top = doc.getTopLevelValue();
            if (!(top instanceof YAMLMapping root)) {
                continue;
            }
            YAMLKeyValue appendersKv = findMappingEntry(root, "appenders");
            if (appendersKv == null || !(appendersKv.getValue() instanceof YAMLMapping am)) {
                continue;
            }
            for (YAMLKeyValue kv : am.getKeyValues()) {
                String k = kv.getKeyText();
                if (k != null && !k.isBlank() && !names.contains(k)) {
                    names.add(k);
                }
            }
        }
        return names;
    }

    private static @NotNull Set<String> collectSequenceScalarTextsForParent(@NotNull YAMLFile yamlFile,
                                                                              @NotNull String parentKey) {
        Set<String> texts = new LinkedHashSet<>();
        for (YAMLKeyValue kv : PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue.class)) {
            String path = GoSpringConfigKeyNavigationSupport.buildYamlPropertyKey(kv);
            if (!parentKey.equals(path)) {
                continue;
            }
            YAMLValue v = kv.getValue();
            if (!(v instanceof YAMLSequence seq)) {
                continue;
            }
            for (YAMLSequenceItem item : seq.getItems()) {
                String t = sequenceItemText(item);
                if (t != null && !t.isBlank()) {
                    texts.add(t);
                }
            }
        }
        return texts;
    }

    private static @NotNull String trimLeadingWhitespace(@NotNull String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }

    private static @Nullable String extractFirstSegmentAfterPrefix(@NotNull String propertyKey, @NotNull String prefix) {
        if (!propertyKey.startsWith(prefix)) {
            return null;
        }
        String rest = propertyKey.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static @Nullable VirtualFile findApplogPackageDir(@NotNull Project project) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        for (VirtualFile vf : FilenameIndex.getVirtualFilesByName(project, "logger.go", scope)) {
            VirtualFile parent = vf.getParent();
            if (parent == null || !"applog".equals(parent.getName())) {
                continue;
            }
            PsiFile pf = PsiManager.getInstance(project).findFile(vf);
            if (pf != null && declaresPackageApplog(pf)) {
                return parent;
            }
        }
        return null;
    }

    public static boolean declaresPackageApplog(@NotNull PsiFile pf) {
        String s = pf.getText();
        if (s == null) {
            return false;
        }
        int idx = s.indexOf("package ");
        if (idx < 0) {
            return false;
        }
        int lineEnd = s.indexOf('\n', idx);
        if (lineEnd < 0) {
            lineEnd = s.length();
        }
        String line = s.substring(idx, lineEnd).trim();
        return line.equals("package applog") || line.startsWith("package applog ");
    }

    private static @Nullable PsiElement findConstIdentifier(@NotNull PsiFile file, @NotNull String constName) {
        String text = file.getText();
        if (text == null) {
            return null;
        }
        int from = 0;
        while (true) {
            int idx = text.indexOf(constName, from);
            if (idx < 0) {
                return null;
            }
            if (idx > 0 && isIdentChar(text.charAt(idx - 1))) {
                from = idx + constName.length();
                continue;
            }
            int after = idx + constName.length();
            if (after < text.length() && isIdentChar(text.charAt(after))) {
                from = idx + constName.length();
                continue;
            }
            int eq = skipWhitespace(text, after);
            if (eq < text.length() && text.charAt(eq) == '=') {
                int mid = idx + Math.max(1, constName.length() / 2);
                return file.findElementAt(mid);
            }
            from = idx + constName.length();
        }
    }

    private static boolean isIdentChar(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    private static int skipWhitespace(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }
}
