package io.infra.idea.plugin.gospring.index;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import io.infra.idea.plugin.gospring.model.GoSpringBeanDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringBeanInjectionUsage;
import io.infra.idea.plugin.gospring.model.GoSpringConfigProperty;
import io.infra.idea.plugin.gospring.model.GoSpringConfigUsage;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringConfigMetadata;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoSpringIndex {
    private static final Key<CachedValue<Model>> CACHE_KEY = Key.create("infra.idea.plugin.goSpringModel");
    private static final Pattern RAW_LITERAL = Pattern.compile("`([^`]*)`", Pattern.DOTALL);
    private static final Pattern AUTOWIRE_TAG = Pattern.compile("(^|\\s)autowire:\"([^\"]*)\"");
    private static final Pattern VALUE_TAG = Pattern.compile("(^|\\s)value:\"\\$\\{([^}:]+)(?::=[^}]*)?}\"");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("(?s)func\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*?)\\)\\s*([^\\{]*)\\{");
    private static final Pattern PROVIDE_CALL_PATTERN = Pattern.compile("(?:gs|app)\\.Provide\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_\\.]*)");
    private static final Pattern NAME_CALL_PATTERN = Pattern.compile("\\.Name\\s*\\(\\s*([\"`])([^\"`]+)\\1\\s*\\)", Pattern.DOTALL);
    private static final Pattern EXPORT_AS_PATTERN = Pattern.compile("As\\s*\\[\\s*([^\\]]+)\\s*]");
    private static final Pattern OBJECT_NEW_PATTERN = Pattern.compile("(?:gs|app)\\.(?:Object|Root)\\s*\\(\\s*new\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\)\\s*\\)");
    private static final Pattern PROVIDE_LITERAL_PATTERN = Pattern.compile("(?:gs|app)\\.Provide\\s*\\(\\s*&\\s*([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\{");
    private static final Pattern ROOT_LITERAL_PATTERN = Pattern.compile("(?:gs|app)\\.Root\\s*\\(\\s*&\\s*([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\{");
    private static final Pattern FIELD_PREFIX_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s+(.+?)\\s*$");
    private static final Pattern PARAM_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)*)\\s+([^,\\n]+)");
    private static final Pattern STRUCT_PATTERN = Pattern.compile("(?ms)^\\s*type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+struct\\s*\\{(.*?)^\\s*}");
    private static final Pattern VALUE_KEY_PATTERN = Pattern.compile("value:\"\\$\\{([^}:]+)(?::=([^}]*))?}\"");
    private static final Pattern GROUP_CALL_PATTERN = Pattern.compile("(?s)(?:gs|app)\\.Group\\s*\\(\\s*([\"`])([^\"`]+)\\1\\s*,.*?func\\s*\\((.*?)\\)\\s*([^\\{]*)\\{");

    private static final Set<String> BASIC_TYPES = Set.of(
            "string", "bool", "byte", "rune", "error",
            "int", "int8", "int16", "int32", "int64",
            "uint", "uint8", "uint16", "uint32", "uint64", "uintptr",
            "float32", "float64", "complex64", "complex128",
            "any", "interface{}"
    );

    private GoSpringIndex() {
    }

    public static Collection<GoSpringBeanDefinition> findBeanDefinitions(Project project, String beanName) {
        if (beanName == null || beanName.isBlank()) {
            return List.of();
        }
        return getModel(project).beanDefinitionsByName.getOrDefault(beanName, List.of());
    }

    public static Collection<GoSpringBeanDefinition> findBeanDefinitionsByType(Project project, @Nullable String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return List.of();
        }
        Map<String, GoSpringBeanDefinition> unique = new LinkedHashMap<>();
        for (String key : buildTypeKeys(typeName)) {
            for (GoSpringBeanDefinition definition : getModel(project).beanDefinitionsByTypeKey.getOrDefault(key, List.of())) {
                unique.put(definitionIdentity(definition), definition);
            }
        }
        return unique.values();
    }

    public static Collection<GoSpringBeanDefinition> findBeanDefinitions(Project project, GoSpringBeanInjectionUsage usage) {
        if (usage == null) {
            return List.of();
        }
        Map<String, GoSpringBeanDefinition> unique = new LinkedHashMap<>();
        if (usage.getBeanName() != null && !usage.getBeanName().isBlank()) {
            for (GoSpringBeanDefinition definition : findBeanDefinitions(project, usage.getBeanName())) {
                if (usage.getTypeName() == null || matchesType(definition, usage.getTypeName())) {
                    unique.put(definitionIdentity(definition), definition);
                }
            }
        }
        if ((usage.getBeanName() == null || usage.getBeanName().isBlank()) && usage.getTypeName() != null) {
            for (GoSpringBeanDefinition definition : findBeanDefinitionsByType(project, usage.getTypeName())) {
                unique.put(definitionIdentity(definition), definition);
            }
        }
        return unique.values();
    }

    public static @Nullable GoSpringBeanDefinition findBeanDefinitionAt(PsiElement sourceElement) {
        if (sourceElement == null) {
            return null;
        }
        for (GoSpringBeanDefinition definition : getModel(sourceElement.getProject()).beanDefinitions) {
            if (matchesAnchor(definition.getPsiElement(), sourceElement)) {
                return definition;
            }
        }
        return null;
    }

    public static @Nullable GoSpringBeanDefinition findBeanDefinitionForAnchor(PsiElement sourceElement) {
        if (sourceElement == null) {
            return null;
        }
        for (GoSpringBeanDefinition definition : getModel(sourceElement.getProject()).beanDefinitions) {
            if (isExactAnchorElement(definition.getPsiElement(), sourceElement)) {
                return definition;
            }
        }
        return null;
    }

    public static Collection<GoSpringBeanInjectionUsage> findBeanInjectionUsagesAt(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringBeanInjectionUsage> usages = new ArrayList<>();
        for (GoSpringBeanInjectionUsage usage : getModel(sourceElement.getProject()).beanInjectionUsages) {
            if (matchesAnchor(usage.getPsiElement(), sourceElement)) {
                usages.add(usage);
            }
        }
        return usages;
    }

    public static Collection<GoSpringBeanInjectionUsage> findBeanInjectionUsagesForAnchor(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringBeanInjectionUsage> usages = new ArrayList<>();
        for (GoSpringBeanInjectionUsage usage : getModel(sourceElement.getProject()).beanInjectionUsages) {
            if (isExactAnchorElement(usage.getPsiElement(), sourceElement)) {
                usages.add(usage);
            }
        }
        return usages;
    }

    public static Collection<GoSpringConfigUsage> findConfigUsagesAt(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringConfigUsage> usages = new ArrayList<>();
        for (GoSpringConfigUsage usage : getModel(sourceElement.getProject()).configUsages) {
            if (matchesAnchor(usage.getPsiElement(), sourceElement)) {
                usages.add(usage);
            }
        }
        return usages;
    }

    public static Collection<GoSpringConfigUsage> findConfigUsagesForAnchor(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringConfigUsage> usages = new ArrayList<>();
        for (GoSpringConfigUsage usage : getModel(sourceElement.getProject()).configUsages) {
            if (isExactAnchorElement(usage.getPsiElement(), sourceElement)) {
                usages.add(usage);
            }
        }
        return usages;
    }

    public static Collection<GoSpringConfigProperty> findConfigProperties(Project project, String key) {
        return getModel(project).configPropertiesByKey.getOrDefault(key, List.of());
    }

    public static Collection<GoSpringConfigProperty> findConfigProperties(Project project, GoSpringConfigUsage usage) {
        if (usage == null) {
            return List.of();
        }
        Map<String, GoSpringConfigProperty> unique = new LinkedHashMap<>();
        for (String key : usage.getEffectiveKeys()) {
            for (GoSpringConfigProperty property : findConfigProperties(project, key)) {
                unique.put(configPropertyIdentity(property), property);
            }
            if (usage.isPrefixMatch()) {
                for (GoSpringConfigProperty property : findConfigPropertiesByPrefix(project, key + ".")) {
                    unique.put(configPropertyIdentity(property), property);
                }
            }
        }
        return unique.values();
    }

    public static Collection<PsiElement> findAutowireUsages(Project project, String beanName) {
        if (beanName == null || beanName.isBlank()) {
            return List.of();
        }
        return getModel(project).autowireUsagesByBeanName.getOrDefault(beanName, List.of());
    }

    public static Collection<PsiElement> findAutowireUsagesByType(Project project, @Nullable String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return List.of();
        }
        Map<String, PsiElement> unique = new LinkedHashMap<>();
        for (String key : buildTypeKeys(typeName)) {
            for (PsiElement usage : getModel(project).autowireUsagesByTypeKey.getOrDefault(key, List.of())) {
                unique.put(psiIdentity(usage), usage);
            }
        }
        return unique.values();
    }

    public static Collection<PsiElement> findValueUsages(Project project, String key) {
        return getModel(project).valueUsagesByKey.getOrDefault(key, List.of());
    }

    public static Collection<PsiElement> findValueUsages(Project project, String key, @Nullable String ownerTypeName) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        if (ownerTypeName == null || ownerTypeName.isBlank()) {
            return findValueUsages(project, key);
        }
        Map<String, PsiElement> unique = new LinkedHashMap<>();
        Model model = getModel(project);
        for (String typeKey : buildTypeKeys(ownerTypeName)) {
            Map<String, List<PsiElement>> usagesByType = model.valueUsagesByKeyAndTypeKey.get(typeKey);
            if (usagesByType == null) {
                continue;
            }
            for (PsiElement usage : usagesByType.getOrDefault(key, List.of())) {
                unique.put(psiIdentity(usage), usage);
            }
        }
        return unique.values();
    }

    public static Collection<GoSpringConfigProperty> findConfigPropertiesByPrefix(Project project, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        Map<String, GoSpringConfigProperty> unique = new LinkedHashMap<>();
        for (Map.Entry<String, List<GoSpringConfigProperty>> entry : getModel(project).configPropertiesByKey.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            for (GoSpringConfigProperty property : entry.getValue()) {
                unique.put(configPropertyIdentity(property), property);
            }
        }
        return unique.values();
    }

    public static Collection<GoSpringExternalConfigDefinition> findExternalConfigDefinitions(Project project, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return List.of();
        }
        List<GoSpringExternalConfigDefinition> result = new ArrayList<>();
        for (GoSpringExternalConfigDefinition definition : getModel(project).externalConfigDefinitions) {
            if (matchesExternalConfigDefinition(definition, propertyKey)) {
                result.add(definition);
            }
        }
        return result;
    }

    public static Collection<GoSpringExternalConfigDefinition> findExternalConfigDefinitionsAt(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringExternalConfigDefinition> result = new ArrayList<>();
        for (GoSpringExternalConfigDefinition definition : getModel(sourceElement.getProject()).externalConfigDefinitions) {
            if (matchesAnchor(definition.getPsiElement(), sourceElement)) {
                result.add(definition);
            }
        }
        return result;
    }

    public static Collection<GoSpringExternalConfigDefinition> findExternalConfigDefinitionsForAnchor(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringExternalConfigDefinition> result = new ArrayList<>();
        for (GoSpringExternalConfigDefinition definition : getModel(sourceElement.getProject()).externalConfigDefinitions) {
            if (isExactAnchorElement(definition.getPsiElement(), sourceElement)) {
                result.add(definition);
            }
        }
        return result;
    }

    public static Collection<GoSpringConfigProperty> findConfigProperties(Project project, GoSpringExternalConfigDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        Map<String, GoSpringConfigProperty> result = new LinkedHashMap<>();
        String prefix = definition.getGroupPrefix() + ".";
        for (Map.Entry<String, List<GoSpringConfigProperty>> entry : getModel(project).configPropertiesByKey.entrySet()) {
            String propertyKey = entry.getKey();
            if (!propertyKey.startsWith(prefix)) {
                continue;
            }
            if (!matchesExternalConfigDefinition(definition, propertyKey)) {
                continue;
            }
            for (GoSpringConfigProperty property : entry.getValue()) {
                result.put(configPropertyIdentity(property), property);
            }
        }
        return result.values();
    }

    public static Collection<GoSpringGroupDefinition> findGroupDefinitionsAt(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringGroupDefinition> result = new ArrayList<>();
        for (GoSpringGroupDefinition definition : getModel(sourceElement.getProject()).groupDefinitions) {
            if (matchesAnchor(definition.getPsiElement(), sourceElement)) {
                result.add(definition);
            }
        }
        return result;
    }

    public static Collection<GoSpringGroupDefinition> findGroupDefinitionsForAnchor(PsiElement sourceElement) {
        if (sourceElement == null) {
            return List.of();
        }
        List<GoSpringGroupDefinition> result = new ArrayList<>();
        for (GoSpringGroupDefinition definition : getModel(sourceElement.getProject()).groupDefinitions) {
            if (isExactAnchorElement(definition.getPsiElement(), sourceElement)) {
                result.add(definition);
            }
        }
        return result;
    }

    public static Collection<GoSpringGroupDefinition> findGroupDefinitionsByType(Project project, @Nullable String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return List.of();
        }
        Map<String, GoSpringGroupDefinition> result = new LinkedHashMap<>();
        Set<String> targetTypeKeys = buildTypeKeys(typeName);
        for (GoSpringGroupDefinition definition : getModel(project).groupDefinitions) {
            for (String providedType : definition.getProvidedTypes()) {
                for (String key : buildTypeKeys(providedType)) {
                    if (targetTypeKeys.contains(key)) {
                        result.put(groupDefinitionIdentity(definition), definition);
                    }
                }
            }
        }
        return result.values();
    }

    public static Collection<GoSpringGroupDefinition> findGroupDefinitions(Project project, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return List.of();
        }
        Map<String, GoSpringGroupDefinition> result = new LinkedHashMap<>();
        for (GoSpringGroupDefinition definition : getModel(project).groupDefinitions) {
            String prefix = definition.getGroupPrefix();
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (propertyKey.equals(prefix) || propertyKey.startsWith(prefix + ".")) {
                result.put(groupDefinitionIdentity(definition), definition);
            }
        }
        return result.values();
    }

    public static Collection<GoSpringConfigProperty> findConfigProperties(Project project, GoSpringGroupDefinition definition) {
        if (definition == null || definition.getGroupPrefix() == null || definition.getGroupPrefix().isBlank()) {
            return List.of();
        }
        String prefix = definition.getGroupPrefix() + ".";
        Map<String, GoSpringConfigProperty> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<GoSpringConfigProperty>> entry : getModel(project).configPropertiesByKey.entrySet()) {
            String propertyKey = entry.getKey();
            if (!propertyKey.startsWith(prefix)) {
                continue;
            }
            String remaining = propertyKey.substring(prefix.length());
            int separator = remaining.indexOf('.');
            if (separator <= 0 || separator >= remaining.length() - 1) {
                continue;
            }
            for (GoSpringConfigProperty property : entry.getValue()) {
                result.put(configPropertyIdentity(property), property);
            }
        }
        return result.values();
    }

    private static Collection<GoSpringConfigProperty> findConfigProperties(Project project,
                                                                           GoSpringGroupDefinition definition,
                                                                           @Nullable String beanName,
                                                                           boolean wildcard,
                                                                           Collection<String> excludedNames) {
        if (definition == null || definition.getGroupPrefix() == null || definition.getGroupPrefix().isBlank()) {
            return List.of();
        }
        String prefix = definition.getGroupPrefix() + ".";
        Map<String, GoSpringConfigProperty> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<GoSpringConfigProperty>> entry : getModel(project).configPropertiesByKey.entrySet()) {
            String propertyKey = entry.getKey();
            if (!propertyKey.startsWith(prefix)) {
                continue;
            }
            String remaining = propertyKey.substring(prefix.length());
            int separator = remaining.indexOf('.');
            if (separator <= 0 || separator >= remaining.length() - 1) {
                continue;
            }
            String instanceName = remaining.substring(0, separator);
            if (wildcard) {
                if (excludedNames != null && excludedNames.contains(instanceName)) {
                    continue;
                }
            } else {
                String targetInstance = (beanName == null || beanName.isBlank()) ? "main" : beanName;
                if (!targetInstance.equals(instanceName)) {
                    continue;
                }
            }
            for (GoSpringConfigProperty property : entry.getValue()) {
                result.put(configPropertyIdentity(property), property);
            }
        }
        return result.values();
    }

    public static Collection<GoSpringConfigProperty> findConfigProperties(Project project,
                                                                          String typeName,
                                                                          @Nullable String beanName,
                                                                          boolean wildcard,
                                                                          Collection<String> excludedNames) {
        Map<String, GoSpringConfigProperty> result = new LinkedHashMap<>();
        for (GoSpringGroupDefinition definition : findGroupDefinitionsByType(project, typeName)) {
            for (GoSpringConfigProperty property : findConfigProperties(project, definition, beanName, wildcard, excludedNames)) {
                result.put(configPropertyIdentity(property), property);
            }
        }
        return result.values();
    }

    public static Collection<GoSpringConfigMetadata> getConfigMetadata(Project project) {
        return getModel(project).configMetadataByKey.values();
    }

    public static @Nullable GoSpringConfigMetadata findConfigMetadata(Project project, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return getModel(project).configMetadataByKey.get(key);
    }

    public static Collection<PsiElement> findAutowireUsages(Project project,
                                                            GoSpringGroupDefinition definition,
                                                            @Nullable String beanName) {
        if (definition == null) {
            return List.of();
        }
        Map<String, PsiElement> result = new LinkedHashMap<>();
        boolean includeDefault = beanName == null || beanName.isBlank() || "main".equals(beanName);
        for (GoSpringBeanInjectionUsage usage : getModel(project).beanInjectionUsages) {
            if (usage.getKind() != GoSpringBeanInjectionUsage.Kind.FIELD) {
                continue;
            }
            if (usage.getTypeName() == null || !groupMatchesType(definition, usage.getTypeName())) {
                continue;
            }
            String usageBeanName = usage.getBeanName();
            if (beanName == null || beanName.isBlank()) {
                if (usageBeanName != null && !usageBeanName.isBlank()) {
                    continue;
                }
            } else if (beanName.equals(usageBeanName)) {
                result.put(psiIdentity(usage.getPsiElement()), usage.getPsiElement());
            } else if (includeDefault && (usageBeanName == null || usageBeanName.isBlank())) {
                result.put(psiIdentity(usage.getPsiElement()), usage.getPsiElement());
            }
        }
        return result.values();
    }

    private static Model getModel(Project project) {
        CachedValue<Model> cached = project.getUserData(CACHE_KEY);
        if (cached == null) {
            synchronized (project) {
                cached = project.getUserData(CACHE_KEY);
                if (cached == null) {
                    cached = CachedValuesManager.getManager(project).createCachedValue(
                            () -> CachedValueProvider.Result.create(buildModel(project), PsiModificationTracker.MODIFICATION_COUNT),
                            false
                    );
                    project.putUserData(CACHE_KEY, cached);
                }
            }
        }
        return cached.getValue();
    }

    private static Model buildModel(Project project) {
        Model model = new Model();
        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        Set<VirtualFile> configFiles = new LinkedHashSet<>();
        configFiles.addAll(FilenameIndex.getVirtualFilesByName(project, "app.properties", scope));
        configFiles.addAll(FilenameIndex.getVirtualFilesByName(project, "app.yml", scope));
        configFiles.addAll(FilenameIndex.getVirtualFilesByName(project, "app.yaml", scope));
        for (VirtualFile file : configFiles) {
            PsiFile psiFile = psiManager.findFile(file);
            for (GoSpringConfigProperty property : readConfigProperties(psiFile)) {
                model.configPropertiesByKey.computeIfAbsent(property.getKey(), unused -> new ArrayList<>()).add(property);
            }
        }

        for (VirtualFile file : FilenameIndex.getAllFilesByExt(project, "go", scope)) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {
                collectGoFileModel(psiFile, model);
            }
        }
        collectExternalModuleConfigDefinitions(project, model);
        return model;
    }

    private static void collectGoFileModel(PsiFile psiFile, Model model) {
        String text = psiFile.getText();
        String packageName = readPackageName(text);
        Map<String, List<ProviderRegistration>> registrations = collectProviderRegistrations(text);
        collectStructDefinitions(psiFile, text, model);
        collectGroupDefinitions(psiFile, text, model);

        collectFunctionDefinitions(psiFile, text, packageName, registrations, model);
        collectDirectObjectDefinitions(psiFile, text, model);

        Matcher rawLiteralMatcher = RAW_LITERAL.matcher(text);
        while (rawLiteralMatcher.find()) {
            String content = rawLiteralMatcher.group(1);
            if (content == null || (!content.contains("autowire:\"") && !content.contains("value:\"${"))) {
                continue;
            }
            collectAutowireUsages(psiFile, text, rawLiteralMatcher.start(), content, model);
            collectValueUsages(psiFile, rawLiteralMatcher.start(1), content, model);
        }

        collectStructuredValueUsages(model);
    }

    private static void collectStructDefinitions(PsiFile psiFile, String text, Model model) {
        Matcher matcher = STRUCT_PATTERN.matcher(text);
        while (matcher.find()) {
            String structName = matcher.group(1);
            String structBody = matcher.group(2);
            if (structName == null || structBody == null) {
                continue;
            }
            StructDefinition definition = model.structDefinitions.computeIfAbsent(structName, StructDefinition::new);
            parseStructFields(psiFile, structBody, matcher.start(2), definition);
        }
    }

    private static void collectGroupDefinitions(PsiFile psiFile, String text, Model model) {
        Matcher matcher = GROUP_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawPrefix = matcher.group(2);
            String groupPrefix = normalizeGroupPrefix(rawPrefix);
            if (groupPrefix == null || groupPrefix.isBlank()) {
                continue;
            }
            List<String> returnTypes = parseReturnTypes(matcher.group(4));
            PsiElement anchor = findAnchor(psiFile, matcher.start(2));
            model.addGroupDefinition(new GoSpringGroupDefinition(groupPrefix, returnTypes, anchor));
            model.addConfigMetadata(new GoSpringConfigMetadata(
                    groupPrefix,
                    returnTypes.isEmpty() ? null : returnTypes.get(0),
                    null,
                    "group",
                    anchor
            ));
        }
    }

    private static void parseStructFields(PsiFile psiFile,
                                          String structBody,
                                          int structBodyStartOffset,
                                          StructDefinition definition) {
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
                int backtickEnd = backtickStart < 0 ? -1 : line.indexOf('`', backtickStart + 1);
                if (backtickStart > 0 && backtickEnd > backtickStart) {
                    String fieldPrefix = line.substring(0, backtickStart).trim();
                    int split = findLastWhitespace(fieldPrefix);
                    if (split > 0) {
                        String fieldName = fieldPrefix.substring(0, split).trim();
                        String fieldType = normalizeTypeName(fieldPrefix.substring(split + 1));
                        Matcher valueMatcher = VALUE_KEY_PATTERN.matcher(line.substring(backtickStart + 1, backtickEnd));
                        if (fieldType != null && valueMatcher.find()) {
                            String key = valueMatcher.group(1);
                            String defaultValue = valueMatcher.group(2);
                            if (key != null && !key.isBlank()) {
                                int keyOffsetInLine = backtickStart + 1 + valueMatcher.start(1);
                                PsiElement anchor = findAnchor(psiFile, structBodyStartOffset + cursor + keyOffsetInLine);
                                definition.structuredConfigAnchorKeys.add(psiIdentity(anchor));
                                definition.fields.add(new StructFieldBinding(fieldName, fieldType, key, defaultValue, anchor));
                            }
                        }
                    }
                }
            }
            cursor = lineEnd + 1;
        }
    }

    private static void collectStructuredValueUsages(Model model) {
        for (StructDefinition definition : model.structDefinitions.values()) {
            for (StructFieldBinding field : definition.fields) {
                collectValueUsagesFromField(model, field, "", new LinkedHashSet<>(), definition.name);
            }
        }
    }

    private static void collectExternalModuleConfigDefinitions(Project project, Model model) {
        PsiManager psiManager = PsiManager.getInstance(project);
        for (Path modulePath : getGoSpringModuleRoots()) {
            try {
                if (!Files.isDirectory(modulePath)) {
                    continue;
                }
                try (var paths = Files.list(modulePath)) {
                    for (Path filePath : (Iterable<Path>) paths
                            .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".go"))
                            ::iterator) {
                        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath);
                        if (virtualFile == null) {
                            continue;
                        }
                        PsiFile psiFile = psiManager.findFile(virtualFile);
                        if (psiFile == null) {
                            continue;
                        }
                        try {
                            collectExternalModuleConfigDefinitions(psiFile, Files.readString(filePath), model);
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void collectExternalModuleConfigDefinitions(PsiFile psiFile, String text, Model model) {
        collectGroupDefinitions(psiFile, text, model);
        Map<String, StructDefinition> fileStructDefinitions = collectFileStructDefinitions(psiFile, text);
        Matcher matcher = GROUP_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawPrefix = matcher.group(2);
            String paramText = matcher.group(3);
            String groupPrefix = normalizeGroupPrefix(rawPrefix);
            if (groupPrefix == null || paramText == null || paramText.isBlank()) {
                continue;
            }
            List<String> params = splitTopLevel(paramText, ',');
            if (params.isEmpty()) {
                continue;
            }
            String configType = extractTypeFromParamOrReturn(params.get(0));
            if (configType == null || configType.isBlank()) {
                continue;
            }
            StructDefinition definition = fileStructDefinitions.get(baseTypeName(configType));
            if (definition == null) {
                continue;
            }
            for (StructFieldBinding field : definition.fields) {
                collectExternalFieldDefinitions(model, groupPrefix, field, fileStructDefinitions, "", new LinkedHashSet<>());
            }
        }
    }

    private static Map<String, StructDefinition> collectFileStructDefinitions(PsiFile psiFile, String text) {
        Map<String, StructDefinition> result = new LinkedHashMap<>();
        Matcher matcher = STRUCT_PATTERN.matcher(text);
        while (matcher.find()) {
            String structName = matcher.group(1);
            String structBody = matcher.group(2);
            if (structName == null || structBody == null) {
                continue;
            }
            StructDefinition definition = new StructDefinition(structName);
            parseStructFields(psiFile, structBody, matcher.start(2), definition);
            result.put(structName, definition);
        }
        return result;
    }

    private static void collectExternalFieldDefinitions(Model model,
                                                        String groupPrefix,
                                                        StructFieldBinding field,
                                                        Map<String, StructDefinition> fileStructDefinitions,
                                                        String currentRelativePrefix,
                                                        Set<String> visitedTypes) {
        String relativeKey = joinPropertyKey(currentRelativePrefix, field.key);
        model.addConfigMetadata(new GoSpringConfigMetadata(
                groupPrefix + ".main." + relativeKey,
                field.typeName,
                field.defaultValue,
                groupPrefix,
                field.anchor
        ));
        model.addConfigMetadata(new GoSpringConfigMetadata(
                groupPrefix + ".*." + relativeKey,
                field.typeName,
                field.defaultValue,
                groupPrefix,
                field.anchor
        ));
        boolean prefixMatch = false;
        StructDefinition nestedDefinition = fileStructDefinitions.get(baseTypeName(field.typeName));
        if (nestedDefinition != null && visitedTypes.add(groupPrefix + "#" + nestedDefinition.name)) {
            prefixMatch = true;
            for (StructFieldBinding nestedField : nestedDefinition.fields) {
                collectExternalFieldDefinitions(model, groupPrefix, nestedField, fileStructDefinitions, relativeKey, visitedTypes);
            }
            visitedTypes.remove(groupPrefix + "#" + nestedDefinition.name);
        }
        model.addExternalConfigDefinition(new GoSpringExternalConfigDefinition(groupPrefix, relativeKey, prefixMatch, field.anchor));
    }

    private static void collectValueUsagesFromField(Model model,
                                                    StructFieldBinding field,
                                                    String prefix,
                                                    Set<String> visitedTypes,
                                                    @Nullable String ownerTypeName) {
        String effectiveKey = joinPropertyKey(prefix, field.key);
        model.addConfigMetadata(new GoSpringConfigMetadata(
                effectiveKey,
                field.typeName,
                field.defaultValue,
                "project",
                field.anchor
        ));
        boolean prefixMatch = false;
        StructDefinition nestedDefinition = model.structDefinitions.get(baseTypeName(field.typeName));
        if (nestedDefinition != null && visitedTypes.add(nestedDefinition.name)) {
            prefixMatch = true;
            for (StructFieldBinding nestedField : nestedDefinition.fields) {
                collectValueUsagesFromField(model, nestedField, effectiveKey, visitedTypes, ownerTypeName);
            }
            visitedTypes.remove(nestedDefinition.name);
        }
        model.addConfigUsage(new GoSpringConfigUsage(field.key, List.of(effectiveKey), prefixMatch, field.anchor, ownerTypeName));
    }

    private static void collectFunctionDefinitions(PsiFile psiFile,
                                                   String text,
                                                   @Nullable String packageName,
                                                   Map<String, List<ProviderRegistration>> registrations,
                                                   Model model) {
        Matcher matcher = FUNCTION_PATTERN.matcher(text);
        while (matcher.find()) {
            String functionName = matcher.group(1);
            if (functionName == null) {
                continue;
            }

            List<ProviderRegistration> registrationInfos = registrationsFor(registrations, packageName, functionName);
            boolean constructorLike = functionName.startsWith("New");
            if (!constructorLike && registrationInfos.isEmpty()) {
                continue;
            }

            List<String> returnTypes = parseReturnTypes(matcher.group(3));
            if (returnTypes.isEmpty()) {
                continue;
            }

            PsiElement anchor = findAnchor(psiFile, matcher.start(1));
            if (registrationInfos.isEmpty()) {
                model.addBeanDefinition(new GoSpringBeanDefinition(null, returnTypes, anchor));
            } else {
                for (ProviderRegistration registration : registrationInfos) {
                    LinkedHashSet<String> providedTypes = new LinkedHashSet<>(returnTypes);
                    providedTypes.addAll(registration.exportTypes);
                    model.addBeanDefinition(new GoSpringBeanDefinition(registration.beanName, new ArrayList<>(providedTypes), anchor));
                }
            }

            collectConstructorParameterUsages(psiFile, matcher.group(2), matcher.start(2), model);
        }
    }

    private static void collectConstructorParameterUsages(PsiFile psiFile,
                                                          @Nullable String paramsText,
                                                          int paramsStartOffset,
                                                          Model model) {
        if (paramsText == null || paramsText.isBlank()) {
            return;
        }
        Matcher matcher = PARAM_PATTERN.matcher(paramsText);
        while (matcher.find()) {
            String names = matcher.group(1);
            String typeName = normalizeTypeName(matcher.group(2));
            if (names == null || typeName == null || !isBeanInjectableType(typeName)) {
                continue;
            }
            for (String name : splitNames(names)) {
                int relativeNameOffset = matcher.start(1) + names.indexOf(name);
                PsiElement anchor = findAnchor(psiFile, paramsStartOffset + relativeNameOffset);
                model.addBeanInjectionUsage(new GoSpringBeanInjectionUsage(null, typeName, GoSpringBeanInjectionUsage.Kind.PARAMETER, anchor));
            }
        }
    }

    private static void collectDirectObjectDefinitions(PsiFile psiFile, String text, Model model) {
        collectDirectTypeDefinition(psiFile, text, model, OBJECT_NEW_PATTERN, true);
        collectDirectTypeDefinition(psiFile, text, model, PROVIDE_LITERAL_PATTERN, true);
        collectDirectTypeDefinition(psiFile, text, model, ROOT_LITERAL_PATTERN, true);
    }

    private static void collectDirectTypeDefinition(PsiFile psiFile,
                                                    String text,
                                                    Model model,
                                                    Pattern pattern,
                                                    boolean pointerType) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String rawType = matcher.group(1);
            if (rawType == null || rawType.isBlank()) {
                continue;
            }
            String typeName = normalizeTypeName(pointerType ? "*" + rawType : rawType);
            if (typeName == null) {
                continue;
            }
            CallMetadata metadata = parseCallMetadata(readCallChainSnippet(text, matcher.start()));
            List<String> providedTypes = new ArrayList<>();
            providedTypes.add(typeName);
            providedTypes.addAll(metadata.exportTypes);
            PsiElement anchor = findAnchor(psiFile, matcher.start(1));
            model.addBeanDefinition(new GoSpringBeanDefinition(metadata.beanName, providedTypes, anchor));
        }
    }

    private static Map<String, List<ProviderRegistration>> collectProviderRegistrations(String text) {
        Map<String, List<ProviderRegistration>> result = new LinkedHashMap<>();
        Matcher matcher = PROVIDE_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String providerRef = matcher.group(1);
            if (providerRef == null || providerRef.isBlank()) {
                continue;
            }
            CallMetadata metadata = parseCallMetadata(readCallChainSnippet(text, matcher.start()));
            addProviderRegistration(result, providerRef, new ProviderRegistration(metadata.beanName, metadata.exportTypes));
        }
        return result;
    }

    private static void addProviderRegistration(Map<String, List<ProviderRegistration>> target,
                                                String providerRef,
                                                ProviderRegistration registration) {
        for (String key : providerKeys(providerRef)) {
            target.computeIfAbsent(key, unused -> new ArrayList<>()).add(registration);
        }
    }

    private static Collection<String> providerKeys(String providerRef) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(providerRef);
        int lastDot = providerRef.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < providerRef.length()) {
            keys.add(providerRef.substring(lastDot + 1));
        }
        return keys;
    }

    private static List<ProviderRegistration> registrationsFor(Map<String, List<ProviderRegistration>> registrations,
                                                               @Nullable String packageName,
                                                               String functionName) {
        LinkedHashSet<ProviderRegistration> result = new LinkedHashSet<>();
        List<ProviderRegistration> direct = registrations.get(functionName);
        if (direct != null) {
            result.addAll(direct);
        }
        if (packageName != null) {
            List<ProviderRegistration> qualified = registrations.get(packageName + "." + functionName);
            if (qualified != null) {
                result.addAll(qualified);
            }
        }
        return new ArrayList<>(result);
    }

    private static void collectAutowireUsages(PsiFile psiFile,
                                              String fileText,
                                              int literalStartOffset,
                                              String content,
                                              Model model) {
        FieldContext fieldContext = extractFieldContext(psiFile, fileText, literalStartOffset);
        if (fieldContext == null || fieldContext.typeName == null || fieldContext.typeName.isBlank()) {
            return;
        }

        Matcher matcher = AUTOWIRE_TAG.matcher(content);
        while (matcher.find()) {
            String expression = matcher.group(2);
            boolean needsTypeMatch = expression == null || expression.isBlank();
            if (expression != null) {
                int segmentStart = 0;
                for (int i = 0; i <= expression.length(); i++) {
                    if (i < expression.length() && expression.charAt(i) != ',') {
                        continue;
                    }
                    String segment = expression.substring(segmentStart, i);
                    String trimmed = segment.trim();
                    if (trimmed.isEmpty() || "?".equals(trimmed) || "*?".equals(trimmed) || "*".equals(trimmed)) {
                        needsTypeMatch = true;
                    } else {
                        String beanName = trimmed.endsWith("?") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
                        if (!beanName.isBlank() && !beanName.contains("*")) {
                            model.addBeanInjectionUsage(new GoSpringBeanInjectionUsage(beanName, fieldContext.typeName, GoSpringBeanInjectionUsage.Kind.FIELD, fieldContext.anchor));
                        }
                    }
                    segmentStart = i + 1;
                }
            }
            if (needsTypeMatch) {
                model.addBeanInjectionUsage(new GoSpringBeanInjectionUsage(null, fieldContext.typeName, GoSpringBeanInjectionUsage.Kind.FIELD, fieldContext.anchor));
            }
        }
    }

    private static void collectValueUsages(PsiFile psiFile, int contentStartOffset, String content, Model model) {
        Matcher matcher = VALUE_TAG.matcher(content);
        while (matcher.find()) {
            String key = matcher.group(2);
            if (key == null || key.isBlank()) {
                continue;
            }
            PsiElement anchor = findAnchor(psiFile, contentStartOffset + matcher.start(2));
            if (model.isStructuredConfigAnchor(anchor)) {
                continue;
            }
            model.addConfigUsage(new GoSpringConfigUsage(key, List.of(key), false, anchor));
        }
    }

    private static @Nullable FieldContext extractFieldContext(PsiFile psiFile, String fileText, int literalStartOffset) {
        int lineStart = Math.max(0, fileText.lastIndexOf('\n', Math.max(0, literalStartOffset - 1)) + 1);
        String linePrefix = fileText.substring(lineStart, literalStartOffset);
        Matcher matcher = FIELD_PREFIX_PATTERN.matcher(linePrefix);
        if (!matcher.find()) {
            return null;
        }
        String typeName = normalizeTypeName(matcher.group(2));
        if (typeName == null) {
            return null;
        }
        PsiElement anchor = findAnchor(psiFile, lineStart + matcher.start(1));
        return new FieldContext(typeName, anchor);
    }

    private static @Nullable String readPackageName(String text) {
        Matcher matcher = PACKAGE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> parseReturnTypes(@Nullable String returnSection) {
        if (returnSection == null) {
            return List.of();
        }
        String trimmed = returnSection.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String segment : splitTopLevel(trimmed, ',')) {
            String typeName = extractTypeFromParamOrReturn(segment);
            if (typeName != null && !typeName.isBlank() && !"error".equals(typeName)) {
                result.add(typeName);
            }
        }
        return result;
    }

    private static @Nullable String extractTypeFromParamOrReturn(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        int split = findLastWhitespace(trimmed);
        if (split < 0) {
            return normalizeTypeName(trimmed);
        }
        return normalizeTypeName(trimmed.substring(split + 1));
    }

    private static List<String> splitTopLevel(String text, char separator) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        int depthParen = 0;
        int depthBracket = 0;
        int depthBrace = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '(' -> depthParen++;
                case ')' -> depthParen = Math.max(0, depthParen - 1);
                case '[' -> depthBracket++;
                case ']' -> depthBracket = Math.max(0, depthBracket - 1);
                case '{' -> depthBrace++;
                case '}' -> depthBrace = Math.max(0, depthBrace - 1);
                default -> {
                }
            }
            if (ch == separator && depthParen == 0 && depthBracket == 0 && depthBrace == 0) {
                result.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(text.substring(start).trim());
        return result;
    }

    private static int findLastWhitespace(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitNames(String names) {
        List<String> result = new ArrayList<>();
        for (String name : names.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static @Nullable String normalizeTypeName(@Nullable String typeName) {
        if (typeName == null) {
            return null;
        }
        String normalized = typeName.replaceAll("\\s+", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String joinPropertyKey(String prefix, String key) {
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        if (key == null || key.isBlank()) {
            return prefix;
        }
        return prefix + "." + key;
    }

    private static @Nullable String normalizeGroupPrefix(@Nullable String rawPrefix) {
        String normalized = normalizeTypeName(rawPrefix);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (normalized.startsWith("${") && normalized.endsWith("}")) {
            normalized = normalized.substring(2, normalized.length() - 1);
        }
        return normalized;
    }

    private static String baseTypeName(String typeName) {
        String current = normalizeTypeName(typeName);
        if (current == null) {
            return "";
        }
        while (current.startsWith("[]")) {
            current = current.substring(2);
        }
        while (current.startsWith("*")) {
            current = current.substring(1);
        }
        int genericStart = current.indexOf('[');
        if (genericStart >= 0) {
            current = current.substring(0, genericStart);
        }
        int lastDot = current.lastIndexOf('.');
        return lastDot >= 0 ? current.substring(lastDot + 1) : current;
    }

    private static List<Path> getGoSpringModuleRoots() {
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        String moduleCache = System.getenv("GOMODCACHE");
        if (moduleCache != null && !moduleCache.isBlank()) {
            Path root = Path.of(moduleCache, "github.com", "go-spring");
            if (Files.isDirectory(root)) {
                addGoSpringModules(root, result);
            }
        }
        String goPath = System.getenv("GOPATH");
        if (goPath != null && !goPath.isBlank()) {
            Path root = Path.of(goPath, "pkg", "mod", "github.com", "go-spring");
            if (Files.isDirectory(root)) {
                addGoSpringModules(root, result);
            }
        }
        Path defaultRoot = Path.of(System.getProperty("user.home"), "go", "pkg", "mod", "github.com", "go-spring");
        if (Files.isDirectory(defaultRoot)) {
            addGoSpringModules(defaultRoot, result);
        }
        return new ArrayList<>(result);
    }

    private static void addGoSpringModules(Path root, Set<Path> result) {
        try (var paths = Files.list(root)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isDirectory(path)) {
                    continue;
                }
                String name = path.getFileName().toString();
                if (name.startsWith("starter-")) {
                    result.add(path);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isBeanInjectableType(String typeName) {
        String normalized = normalizeTypeName(typeName);
        if (normalized == null) {
            return false;
        }
        String current = normalized;
        while (current.startsWith("[]")) {
            current = current.substring(2);
        }
        while (current.startsWith("*")) {
            current = current.substring(1);
        }
        int genericStart = current.indexOf('[');
        if (genericStart >= 0) {
            current = current.substring(0, genericStart);
        }
        if (current.isBlank()) {
            return false;
        }
        if (BASIC_TYPES.contains(current)) {
            return false;
        }
        if (current.contains(".")) {
            return true;
        }
        return Character.isUpperCase(current.charAt(0));
    }

    private static Set<String> buildTypeKeys(@Nullable String typeName) {
        String normalized = normalizeTypeName(typeName);
        if (normalized == null) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addTypeKeys(result, normalized);

        String current = normalized;
        while (current.startsWith("[]")) {
            current = current.substring(2);
            addTypeKeys(result, current);
        }
        return result;
    }

    private static void addTypeKeys(Set<String> target, String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return;
        }
        target.add(typeName);

        String base = typeName;
        String pointerPrefix = "";
        while (base.startsWith("*")) {
            pointerPrefix += "*";
            base = base.substring(1);
        }

        target.add(pointerPrefix + base);
        if (!pointerPrefix.isEmpty()) {
            target.add(base);
        }

        int genericStart = base.indexOf('[');
        String bareBase = genericStart >= 0 ? base.substring(0, genericStart) : base;
        int lastDot = bareBase.lastIndexOf('.');
        String shortBase = lastDot >= 0 ? bareBase.substring(lastDot + 1) : bareBase;
        if (!shortBase.isBlank()) {
            target.add(pointerPrefix + shortBase);
            target.add(shortBase);
        }
    }

    private static boolean matchesType(GoSpringBeanDefinition definition, String usageType) {
        Set<String> usageKeys = buildTypeKeys(usageType);
        for (String providedType : definition.getProvidedTypes()) {
            for (String definitionKey : buildTypeKeys(providedType)) {
                if (usageKeys.contains(definitionKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAnchor(PsiElement anchor, PsiElement sourceElement) {
        if (anchor == null || sourceElement == null) {
            return false;
        }
        PsiFile anchorFile = anchor.getContainingFile();
        PsiFile sourceFile = sourceElement.getContainingFile();
        if (anchorFile == null || sourceFile == null || !anchorFile.equals(sourceFile)) {
            return false;
        }
        TextRange anchorRange = anchor.getTextRange();
        TextRange sourceRange = sourceElement.getTextRange();
        if (anchorRange == null || sourceRange == null) {
            return false;
        }
        return anchorRange.contains(sourceRange)
                || sourceRange.contains(anchorRange)
                || anchorRange.intersectsStrict(sourceRange);
    }

    private static boolean isSameElement(PsiElement left, PsiElement right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        PsiFile leftFile = left.getContainingFile();
        PsiFile rightFile = right.getContainingFile();
        if (leftFile == null || rightFile == null || !leftFile.equals(rightFile)) {
            return false;
        }
        TextRange leftRange = left.getTextRange();
        TextRange rightRange = right.getTextRange();
        return leftRange != null && leftRange.equals(rightRange);
    }

    private static boolean isExactAnchorElement(PsiElement left, PsiElement right) {
        return left != null && right != null && left.equals(right);
    }

    private static boolean matchesExternalConfigDefinition(GoSpringExternalConfigDefinition definition, String propertyKey) {
        String groupPrefix = definition.getGroupPrefix();
        if (groupPrefix == null || groupPrefix.isBlank() || propertyKey == null || propertyKey.isBlank()) {
            return false;
        }
        String prefix = groupPrefix + ".";
        if (!propertyKey.startsWith(prefix)) {
            return false;
        }
        String remaining = propertyKey.substring(prefix.length());
        int separator = remaining.indexOf('.');
        if (separator <= 0 || separator >= remaining.length() - 1) {
            return false;
        }
        String relativeKey = remaining.substring(separator + 1);
        if (relativeKey.equals(definition.getRelativeKey())) {
            return true;
        }
        return definition.isPrefixMatch() && relativeKey.startsWith(definition.getRelativeKey() + ".");
    }

    private static boolean groupMatchesType(GoSpringGroupDefinition definition, String typeName) {
        Set<String> usageKeys = buildTypeKeys(typeName);
        for (String providedType : definition.getProvidedTypes()) {
            for (String key : buildTypeKeys(providedType)) {
                if (usageKeys.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String definitionIdentity(GoSpringBeanDefinition definition) {
        return psiIdentity(definition.getPsiElement()) + "|" + String.valueOf(definition.getBeanName()) + "|" + definition.getProvidedTypes();
    }

    private static String usageIdentity(GoSpringBeanInjectionUsage usage) {
        return psiIdentity(usage.getPsiElement()) + "|" + String.valueOf(usage.getBeanName()) + "|" + String.valueOf(usage.getTypeName());
    }

    private static String configUsageIdentity(GoSpringConfigUsage usage) {
        return psiIdentity(usage.getPsiElement())
                + "|" + usage.getEffectiveKeys()
                + "|" + usage.isPrefixMatch()
                + "|" + String.valueOf(usage.getOwnerTypeName());
    }

    private static String configPropertyIdentity(GoSpringConfigProperty property) {
        return property.getKey() + "|" + psiIdentity(property.getPsiElement());
    }

    private static String groupDefinitionIdentity(GoSpringGroupDefinition definition) {
        return definition.getGroupPrefix() + "|" + psiIdentity(definition.getPsiElement());
    }

    private static String psiIdentity(PsiElement element) {
        PsiFile file = element == null ? null : element.getContainingFile();
        TextRange range = element == null ? null : element.getTextRange();
        String fileName = file == null || file.getVirtualFile() == null ? "<unknown>" : file.getVirtualFile().getPath();
        String offsets = range == null ? "0:0" : range.getStartOffset() + ":" + range.getEndOffset();
        return fileName + ":" + offsets;
    }

    private static @NotNull PsiElement findAnchor(PsiFile psiFile, int offset) {
        int safeOffset = Math.max(0, Math.min(offset, Math.max(0, psiFile.getTextLength() - 1)));
        PsiElement element = psiFile.findElementAt(safeOffset);
        return element != null ? element : psiFile;
    }

    private static String readCallChainSnippet(String text, int startOffset) {
        int depthParen = 0;
        int depthBracket = 0;
        int depthBrace = 0;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        for (int i = startOffset; i < text.length(); i++) {
            char ch = text.charAt(i);
            char prev = i > 0 ? text.charAt(i - 1) : 0;

            if (!inBacktick && ch == '"' && prev != '\\') {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inDoubleQuote && ch == '`') {
                inBacktick = !inBacktick;
            }

            if (!inDoubleQuote && !inBacktick) {
                switch (ch) {
                    case '(' -> depthParen++;
                    case ')' -> depthParen = Math.max(0, depthParen - 1);
                    case '[' -> depthBracket++;
                    case ']' -> depthBracket = Math.max(0, depthBracket - 1);
                    case '{' -> depthBrace++;
                    case '}' -> depthBrace = Math.max(0, depthBrace - 1);
                    case '\n' -> {
                        if (depthParen == 0 && depthBracket == 0 && depthBrace == 0) {
                            int cursor = i + 1;
                            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor)) && text.charAt(cursor) != '\n') {
                                cursor++;
                            }
                            if (cursor >= text.length() || text.charAt(cursor) != '.') {
                                return text.substring(startOffset, i);
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return text.substring(startOffset);
    }

    private static CallMetadata parseCallMetadata(String snippet) {
        String beanName = null;
        Matcher nameMatcher = NAME_CALL_PATTERN.matcher(snippet);
        if (nameMatcher.find()) {
            beanName = nameMatcher.group(2);
        }

        List<String> exportTypes = new ArrayList<>();
        Matcher exportMatcher = EXPORT_AS_PATTERN.matcher(snippet);
        while (exportMatcher.find()) {
            String exportType = normalizeTypeName(exportMatcher.group(1));
            if (exportType != null && !exportType.isBlank()) {
                exportTypes.add(exportType);
            }
        }
        return new CallMetadata(beanName, exportTypes);
    }

    private static List<GoSpringConfigProperty> readConfigProperties(@Nullable PsiFile psiFile) {
        if (psiFile == null || !GoSpringPsi.isSupportedConfigFile(psiFile)) {
            return Collections.emptyList();
        }
        if (psiFile instanceof PropertiesFile propertiesFile) {
            List<GoSpringConfigProperty> result = new ArrayList<>();
            for (IProperty property : propertiesFile.getProperties()) {
                if (property.getKey() != null && property.getPsiElement() != null) {
                    result.add(new GoSpringConfigProperty(property.getKey(), property.getValue(), property.getPsiElement()));
                }
            }
            return result;
        }
        if (psiFile instanceof YAMLFile yamlFile) {
            return readYamlProperties(yamlFile);
        }
        return Collections.emptyList();
    }

    private static List<GoSpringConfigProperty> readYamlProperties(YAMLFile yamlFile) {
        List<GoSpringConfigProperty> result = new ArrayList<>();
        for (YAMLDocument document : yamlFile.getDocuments()) {
            YAMLValue topLevel = document.getTopLevelValue();
            if (topLevel instanceof YAMLMapping mapping) {
                for (YAMLKeyValue keyValue : mapping.getKeyValues()) {
                    collectYamlKeyValue(keyValue, result);
                }
            }
        }
        return result;
    }

    private static void collectYamlKeyValue(YAMLKeyValue keyValue, List<GoSpringConfigProperty> result) {
        String key = buildYamlPropertyKey(keyValue);
        if (key != null && !key.isBlank()) {
            result.add(new GoSpringConfigProperty(key, readYamlScalarValue(keyValue), keyValue));
        }
        YAMLValue value = keyValue.getValue();
        if (value == null) {
            return;
        }
        if (value instanceof YAMLMapping mapping) {
            for (YAMLKeyValue nested : mapping.getKeyValues()) {
                collectYamlKeyValue(nested, result);
            }
            return;
        }
        if (value instanceof YAMLSequence sequence) {
            for (YAMLSequenceItem item : sequence.getItems()) {
                YAMLValue itemValue = item.getValue();
                if (itemValue instanceof YAMLMapping itemMapping) {
                    for (YAMLKeyValue nested : itemMapping.getKeyValues()) {
                        collectYamlKeyValue(nested, result);
                    }
                }
            }
        }
    }

    private static @Nullable String readYamlScalarValue(YAMLKeyValue keyValue) {
        YAMLValue value = keyValue.getValue();
        if (value instanceof YAMLScalar scalar) {
            return scalar.getTextValue();
        }
        return null;
    }

    private static @Nullable String buildYamlPropertyKey(YAMLKeyValue keyValue) {
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

    private static final class Model {
        private final Map<String, List<GoSpringBeanDefinition>> beanDefinitionsByName = new LinkedHashMap<>();
        private final Map<String, List<GoSpringBeanDefinition>> beanDefinitionsByTypeKey = new LinkedHashMap<>();
        private final List<GoSpringBeanDefinition> beanDefinitions = new ArrayList<>();
        private final Map<String, StructDefinition> structDefinitions = new LinkedHashMap<>();
        private final Map<String, List<GoSpringConfigProperty>> configPropertiesByKey = new LinkedHashMap<>();
        private final Map<String, GoSpringConfigMetadata> configMetadataByKey = new LinkedHashMap<>();
        private final List<GoSpringExternalConfigDefinition> externalConfigDefinitions = new ArrayList<>();
        private final List<GoSpringGroupDefinition> groupDefinitions = new ArrayList<>();
        private final Map<String, List<PsiElement>> autowireUsagesByBeanName = new LinkedHashMap<>();
        private final Map<String, List<PsiElement>> autowireUsagesByTypeKey = new LinkedHashMap<>();
        private final List<GoSpringBeanInjectionUsage> beanInjectionUsages = new ArrayList<>();
        private final List<GoSpringConfigUsage> configUsages = new ArrayList<>();
        private final Map<String, List<PsiElement>> valueUsagesByKey = new LinkedHashMap<>();
        private final Map<String, Map<String, List<PsiElement>>> valueUsagesByKeyAndTypeKey = new LinkedHashMap<>();
        private final Set<String> definitionKeys = new LinkedHashSet<>();
        private final Set<String> usageKeys = new LinkedHashSet<>();
        private final Set<String> configUsageKeys = new LinkedHashSet<>();
        private final Set<String> groupDefinitionKeys = new LinkedHashSet<>();

        private void addBeanDefinition(GoSpringBeanDefinition definition) {
            if (!definitionKeys.add(definitionIdentity(definition))) {
                return;
            }
            beanDefinitions.add(definition);
            if (definition.getBeanName() != null && !definition.getBeanName().isBlank()) {
                beanDefinitionsByName.computeIfAbsent(definition.getBeanName(), unused -> new ArrayList<>()).add(definition);
            }
            for (String type : definition.getProvidedTypes()) {
                for (String key : buildTypeKeys(type)) {
                    beanDefinitionsByTypeKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(definition);
                }
            }
        }

        private void addBeanInjectionUsage(GoSpringBeanInjectionUsage usage) {
            if (!usageKeys.add(usageIdentity(usage))) {
                return;
            }
            beanInjectionUsages.add(usage);
            if (usage.getBeanName() != null && !usage.getBeanName().isBlank()) {
                autowireUsagesByBeanName.computeIfAbsent(usage.getBeanName(), unused -> new ArrayList<>()).add(usage.getPsiElement());
            }
            if (usage.getTypeName() != null && !usage.getTypeName().isBlank()) {
                for (String key : buildTypeKeys(usage.getTypeName())) {
                    autowireUsagesByTypeKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(usage.getPsiElement());
                }
            }
        }

        private void addConfigUsage(GoSpringConfigUsage usage) {
            if (!configUsageKeys.add(configUsageIdentity(usage))) {
                return;
            }
            configUsages.add(usage);
            for (String key : usage.getEffectiveKeys()) {
                valueUsagesByKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(usage.getPsiElement());
                if (usage.getOwnerTypeName() != null && !usage.getOwnerTypeName().isBlank()) {
                    for (String typeKey : buildTypeKeys(usage.getOwnerTypeName())) {
                        valueUsagesByKeyAndTypeKey
                                .computeIfAbsent(typeKey, unused -> new LinkedHashMap<>())
                                .computeIfAbsent(key, unused -> new ArrayList<>())
                                .add(usage.getPsiElement());
                    }
                }
            }
        }

        private void addExternalConfigDefinition(GoSpringExternalConfigDefinition definition) {
            externalConfigDefinitions.add(definition);
        }

        private void addConfigMetadata(GoSpringConfigMetadata metadata) {
            configMetadataByKey.putIfAbsent(metadata.getKey(), metadata);
        }

        private void addGroupDefinition(GoSpringGroupDefinition definition) {
            if (!groupDefinitionKeys.add(groupDefinitionIdentity(definition))) {
                return;
            }
            groupDefinitions.add(definition);
        }

        private boolean isStructuredConfigAnchor(PsiElement anchor) {
            String key = psiIdentity(anchor);
            for (StructDefinition definition : structDefinitions.values()) {
                if (definition.structuredConfigAnchorKeys.contains(key)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ProviderRegistration {
        private final String beanName;
        private final List<String> exportTypes;

        private ProviderRegistration(@Nullable String beanName, List<String> exportTypes) {
            this.beanName = beanName;
            this.exportTypes = List.copyOf(exportTypes);
        }
    }

    private static final class CallMetadata {
        private final String beanName;
        private final List<String> exportTypes;

        private CallMetadata(@Nullable String beanName, List<String> exportTypes) {
            this.beanName = beanName;
            this.exportTypes = List.copyOf(exportTypes);
        }
    }

    private static final class FieldContext {
        private final String typeName;
        private final PsiElement anchor;

        private FieldContext(String typeName, PsiElement anchor) {
            this.typeName = typeName;
            this.anchor = anchor;
        }
    }

    private static final class StructDefinition {
        private final String name;
        private final List<StructFieldBinding> fields = new ArrayList<>();
        private final Set<String> structuredConfigAnchorKeys = new LinkedHashSet<>();

        private StructDefinition(String name) {
            this.name = name;
        }
    }

    private static final class StructFieldBinding {
        private final String fieldName;
        private final String typeName;
        private final String key;
        private final String defaultValue;
        private final PsiElement anchor;

        private StructFieldBinding(String fieldName, String typeName, String key, @Nullable String defaultValue, PsiElement anchor) {
            this.fieldName = fieldName;
            this.typeName = typeName;
            this.key = key;
            this.defaultValue = defaultValue;
            this.anchor = anchor;
        }
    }
}
