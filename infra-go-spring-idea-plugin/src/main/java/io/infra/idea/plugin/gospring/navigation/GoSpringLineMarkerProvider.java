package io.infra.idea.plugin.gospring.navigation;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.infra.idea.plugin.gospring.index.GoSpringIndex;
import io.infra.idea.plugin.gospring.index.GoSpringGormQueryIndex;
import io.infra.idea.plugin.gospring.model.GoSpringBeanDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringBeanInjectionUsage;
import io.infra.idea.plugin.gospring.model.GoSpringConfigProperty;
import io.infra.idea.plugin.gospring.model.GoSpringConfigUsage;
import io.infra.idea.plugin.gospring.model.GoSpringExternalConfigDefinition;
import io.infra.idea.plugin.gospring.model.GoSpringGroupDefinition;
import io.infra.idea.plugin.gospring.psi.GoSpringPsi;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GoSpringLineMarkerProvider extends RelatedItemLineMarkerProvider {
    private static final DefaultPsiElementCellRenderer TARGET_CELL_RENDERER = new DefaultPsiElementCellRenderer() {
        @Override
        public String getElementText(PsiElement element) {
            String snippet = resolveLineSnippet(element, element.getContainingFile());
            if (!snippet.isBlank()) {
                return snippet;
            }
            PsiFile file = element.getContainingFile();
            return file == null ? "<unknown>" : file.getName();
        }

        @Override
        public String getContainerText(PsiElement element, String name) {
            PsiFile file = element.getContainingFile();
            if (file == null) {
                return null;
            }
            String relativePath = buildRelativePath(element, file);
            int line = resolveLine(element, file);
            return line > 0 ? relativePath + ":" + line : relativePath;
        }

        @Override
        protected Icon getIcon(PsiElement element) {
            PsiFile file = element.getContainingFile();
            if (file != null && file.getVirtualFile() != null) {
                Icon fileIcon = file.getVirtualFile().getFileType().getIcon();
                if (fileIcon != null) {
                    return fileIcon;
                }
            }
            return super.getIcon(element);
        }
    };

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!GoSpringPsi.isGoFile(element)) {
            return;
        }

        Collection<GoSpringConfigUsage> configUsages = GoSpringIndex.findConfigUsagesForAnchor(element);
        if (!configUsages.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringConfigUsage usage : configUsages) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(element.getProject(), usage)) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
            }
            addMarker(element, result, targets, "跳转到配置");
        }

        Collection<GoSpringExternalConfigDefinition> externalDefinitions = GoSpringIndex.findExternalConfigDefinitionsForAnchor(element);
        if (!externalDefinitions.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringExternalConfigDefinition definition : externalDefinitions) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(element.getProject(), definition)) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
            }
            addMarker(element, result, targets, "跳转到项目配置");
        }

        Collection<GoSpringGroupDefinition> groupDefinitions = GoSpringIndex.findGroupDefinitionsForAnchor(element);
        if (!groupDefinitions.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringGroupDefinition definition : groupDefinitions) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(element.getProject(), definition)) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
            }
            addMarker(element, result, targets, "跳转到 Group 配置");
        }

        Collection<GoSpringBeanInjectionUsage> injectionUsages = GoSpringIndex.findBeanInjectionUsagesForAnchor(element);
        if (!injectionUsages.isEmpty()) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            for (GoSpringBeanInjectionUsage usage : injectionUsages) {
                for (GoSpringConfigProperty property : GoSpringIndex.findConfigProperties(
                        element.getProject(),
                        usage.getTypeName(),
                        usage.getBeanName(),
                        false,
                        List.of())) {
                    if (property.getPsiElement() != null) {
                        targets.add(property.getPsiElement());
                    }
                }
                if (targets.isEmpty()) {
                    for (GoSpringBeanDefinition definition : GoSpringIndex.findBeanDefinitions(element.getProject(), usage)) {
                        if (definition.getPsiElement() != null) {
                            targets.add(definition.getPsiElement());
                        }
                    }
                }
            }
            addMarker(element, result, targets, "跳转到 Bean 定义");
        }

        GoSpringBeanDefinition definition = GoSpringIndex.findBeanDefinitionForAnchor(element);
        if (definition != null) {
            Set<PsiElement> targets = new LinkedHashSet<>();
            collectDefinitionUsages(element, definition, targets);
            addMarker(element, result, targets, "跳转到 Bean 使用点");
        }

        Collection<PsiElement> gormTargets = GoSpringGormQueryIndex.findUsageTargetsForExactAnchor(element);
        if (!gormTargets.isEmpty()) {
            addMarker(
                    element,
                    result,
                    gormTargets,
                    "跳转到 GORM 查询",
                    AllIcons.Actions.Find
            );
        }
    }

    private static void collectDefinitionUsages(PsiElement sourceElement,
                                                GoSpringBeanDefinition definition,
                                                Set<PsiElement> targets) {
        if (definition.getBeanName() != null && !definition.getBeanName().isBlank()) {
            targets.addAll(GoSpringIndex.findAutowireUsages(sourceElement.getProject(), definition.getBeanName()));
        }
        for (String typeName : definition.getProvidedTypes()) {
            targets.addAll(GoSpringIndex.findAutowireUsagesByType(sourceElement.getProject(), typeName));
        }
    }

    private static void addMarker(PsiElement anchor,
                                  Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                  Collection<PsiElement> targets,
                                  String tooltip) {
        addMarker(anchor, result, targets, tooltip, AllIcons.General.Locate);
    }

    private static void addMarker(PsiElement anchor,
                                  Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                  Collection<PsiElement> targets,
                                  String tooltip,
                                  Icon icon) {
        if (anchor == null || targets == null || targets.isEmpty()) {
            return;
        }
        List<PsiElement> targetList = new ArrayList<>();
        for (PsiElement target : targets) {
            if (target != null) {
                targetList.add(normalizeTarget(target));
            }
        }
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(icon)
                .setTargets(targetList)
                .setCellRenderer(TARGET_CELL_RENDERER)
                .setPopupTitle(tooltip + "（" + targetList.size() + "）")
                .setTooltipText(tooltip);
        result.add(builder.createLineMarkerInfo(anchor));
    }

    private static PsiElement normalizeTarget(PsiElement target) {
        if (target == null) {
            return null;
        }
        PsiElement navigationElement = target.getNavigationElement();
        if (navigationElement == null) {
            return target;
        }
        if (navigationElement instanceof PsiFile && !(target instanceof PsiFile)) {
            return target;
        }
        return navigationElement;
    }

    private static String buildRelativePath(PsiElement element, PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        String basePath = element.getProject().getBasePath();
        if (virtualFile == null || basePath == null) {
            return file.getName();
        }
        String path = virtualFile.getPath();
        if (!path.startsWith(basePath)) {
            return file.getName();
        }
        String relative = path.substring(basePath.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative.isBlank() ? file.getName() : relative;
    }

    private static int resolveLine(PsiElement element, PsiFile file) {
        if (element.getTextRange() == null) {
            return -1;
        }
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        if (document == null) {
            return -1;
        }
        return document.getLineNumber(element.getTextRange().getStartOffset()) + 1;
    }

    private static String resolveLineSnippet(PsiElement element, PsiFile file) {
        if (element.getTextRange() == null) {
            return "";
        }
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        if (document == null) {
            return "";
        }
        TextRange range = element.getTextRange();
        int lineIndex = document.getLineNumber(range.getStartOffset());
        if (lineIndex < 0 || lineIndex >= document.getLineCount()) {
            return "";
        }
        int lineStart = document.getLineStartOffset(lineIndex);
        int lineEnd = document.getLineEndOffset(lineIndex);
        String lineText = document.getText(new TextRange(lineStart, lineEnd)).trim();
        if (lineText.length() > 120) {
            return lineText.substring(0, 120) + "...";
        }
        return lineText;
    }
}
