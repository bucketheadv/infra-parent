package io.infra.idea.plugin.gospring.navigation;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Wraps navigation targets with richer popup presentation.
 */
public class GoSpringNavigationTargetElement extends FakePsiElement {
    private final PsiElement delegate;

    private GoSpringNavigationTargetElement(@NotNull PsiElement delegate) {
        this.delegate = delegate;
    }

    public static @NotNull PsiElement wrap(@NotNull PsiElement delegate) {
        if (delegate instanceof GoSpringNavigationTargetElement) {
            return delegate;
        }
        return new GoSpringNavigationTargetElement(delegate);
    }

    @Override
    public @Nullable PsiElement getParent() {
        return delegate.getParent();
    }

    @Override
    public @Nullable PsiFile getContainingFile() {
        return delegate.getContainingFile();
    }

    @Override
    public @NotNull Project getProject() {
        return delegate.getProject();
    }

    @Override
    public @Nullable ItemPresentation getPresentation() {
        ItemPresentation presentation = delegate instanceof NavigationItem navigationItem
                ? navigationItem.getPresentation()
                : null;
        String presentableText = buildLineSnippet(delegate);
        if (presentableText.isBlank()) {
            presentableText = buildFileName(delegate);
        }
        String location = buildLocation(delegate);
        Icon resolvedIcon = resolveFileIcon(delegate);
        if (resolvedIcon == null) {
            resolvedIcon = presentation != null ? presentation.getIcon(false) : delegate.getIcon(0);
        }
        final Icon icon = resolvedIcon;
        final String finalPresentableText = presentableText;
        final String finalLocation = location;
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                return finalPresentableText;
            }

            @Override
            public @Nullable String getLocationString() {
                return finalLocation;
            }

            @Override
            public @Nullable Icon getIcon(boolean unused) {
                return icon;
            }
        };
    }

    @Override
    public String getName() {
        String snippet = buildLineSnippet(delegate);
        return snippet.isBlank() ? buildFileName(delegate) : snippet;
    }

    @Override
    public @Nullable String getPresentableText() {
        return getName();
    }

    @Override
    public @Nullable String getLocationString() {
        return buildLocation(delegate);
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
        Icon fileIcon = resolveFileIcon(delegate);
        if (fileIcon != null) {
            return fileIcon;
        }
        ItemPresentation presentation = delegate instanceof NavigationItem navigationItem
                ? navigationItem.getPresentation()
                : null;
        return presentation != null ? presentation.getIcon(false) : delegate.getIcon(0);
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (delegate instanceof Navigatable navigatable) {
            navigatable.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return delegate instanceof Navigatable navigatable && navigatable.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return delegate instanceof Navigatable navigatable && navigatable.canNavigateToSource();
    }

    @Override
    public boolean isValid() {
        return delegate.isValid();
    }

    private static @NotNull String buildFileName(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        return file == null ? "<unknown>" : file.getName();
    }

    private static @NotNull String buildLocation(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return "<unknown>";
        }
        String relativePath = buildRelativePath(element.getProject(), file.getVirtualFile(), file.getName());
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        if (document == null || element.getTextRange() == null) {
            return relativePath;
        }
        int line = document.getLineNumber(element.getTextRange().getStartOffset()) + 1;
        return relativePath + ":" + line;
    }

    private static @NotNull String buildRelativePath(@NotNull Project project, @Nullable VirtualFile file, @NotNull String fallbackName) {
        if (file == null) {
            return fallbackName;
        }
        String path = file.getPath();
        String basePath = project.getBasePath();
        if (basePath == null || !path.startsWith(basePath)) {
            return fallbackName;
        }
        String relative = path.substring(basePath.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative.isBlank() ? fallbackName : relative;
    }

    private static @NotNull String buildLineSnippet(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null || element.getTextRange() == null) {
            return "";
        }
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        if (document == null) {
            return "";
        }
        TextRange range = element.getTextRange();
        int lineNumber = document.getLineNumber(range.getStartOffset());
        if (lineNumber < 0 || lineNumber >= document.getLineCount()) {
            return "";
        }
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String lineText = document.getText(new TextRange(lineStart, lineEnd)).trim();
        if (lineText.isBlank()) {
            return "";
        }
        return lineText.length() > 120 ? lineText.substring(0, 120) + "..." : lineText;
    }

    private static @Nullable Icon resolveFileIcon(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null || file.getVirtualFile() == null) {
            return null;
        }
        return file.getVirtualFile().getFileType().getIcon();
    }
}
