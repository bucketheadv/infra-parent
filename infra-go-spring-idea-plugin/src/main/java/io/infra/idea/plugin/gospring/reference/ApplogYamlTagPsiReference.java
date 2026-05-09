package io.infra.idea.plugin.gospring.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.infra.idea.plugin.gospring.navigation.InfraGoApplogYamlNavigation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code applog/config.go} 结构体反引号标签中的 {@code yaml:"..."} → 工程内 {@code applog*.yaml} 对应键。
 */
public class ApplogYamlTagPsiReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private static final Pattern YAML_STRUCT_FIELD_TAG = Pattern.compile("yaml:\"([^\"]+)\"");

    private final String yamlTagValue;

    public ApplogYamlTagPsiReference(@NotNull PsiElement hostLiteral,
                                     @NotNull TextRange rangeInHost,
                                     @NotNull String yamlTagValue) {
        super(hostLiteral, rangeInHost, false);
        this.yamlTagValue = yamlTagValue;
    }

    /**
     * 为整段反引号结构体标签生成引用（相对 {@code hostLiteral} 的区间）。
     */
    public static @NotNull List<ApplogYamlTagPsiReference> collectFromStructTagLiteral(@NotNull PsiElement hostLiteral) {
        String litText = hostLiteral.getText();
        List<ApplogYamlTagPsiReference> out = new ArrayList<>();
        if (litText == null || litText.length() < 2 || litText.charAt(0) != '`') {
            return out;
        }
        String content = litText.substring(1, litText.length() - 1);
        Matcher m = YAML_STRUCT_FIELD_TAG.matcher(content);
        while (m.find()) {
            TextRange rangeInHost = TextRange.from(1 + m.start(), m.end() - m.start());
            String tag = m.group(1);
            if (tag != null && !tag.isBlank()) {
                out.add(new ApplogYamlTagPsiReference(hostLiteral, rangeInHost, tag));
            }
        }
        return out;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        PsiElement host = getElement();
        PsiFile file = host.getContainingFile();
        if (file == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        TextRange r = getRangeInElement();
        int offsetInFile = host.getTextRange().getStartOffset() + r.getStartOffset() + Math.max(1, r.getLength()) / 2;
        PsiElement[] targets = InfraGoApplogYamlNavigation.resolveApplogYamlTagFromGoOffset(
                host.getProject(),
                file,
                offsetInFile,
                yamlTagValue
        );
        if (targets == null || targets.length == 0) {
            return ResolveResult.EMPTY_ARRAY;
        }
        ResolveResult[] results = new ResolveResult[targets.length];
        for (int i = 0; i < targets.length; i++) {
            results[i] = new PsiElementResolveResult(targets[i]);
        }
        return results;
    }
}
