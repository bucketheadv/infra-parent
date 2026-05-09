package io.infra.idea.plugin.gospring.navigation;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * infra-go {@code applog}：{@code applog.yaml} 键的 Quick Documentation / 快速导航说明。
 */
public final class InfraGoApplogYamlDocs {
    private InfraGoApplogYamlDocs() {
    }

    public static @Nullable String generateQuickInfo(@NotNull String propertyKey) {
        ApplogKeyDoc doc = describe(propertyKey);
        if (doc == null) {
            return null;
        }
        StringBuilder s = new StringBuilder(doc.title).append(" — ").append(doc.summary);
        if (doc.detail != null && !doc.detail.isBlank()) {
            s.append(" ").append(stripSimpleHtml(doc.detail));
        }
        return s.toString();
    }

    private static @NotNull String stripSimpleHtml(@NotNull String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    public static @Nullable String generateHtml(@NotNull String propertyKey) {
        ApplogKeyDoc doc = describe(propertyKey);
        if (doc == null) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        b.append("<div class='definition'><pre>")
                .append(StringUtil.escapeXmlEntities(propertyKey))
                .append("</pre></div>");
        b.append("<div class='content'>")
                .append(StringUtil.escapeXmlEntities(doc.title))
                .append("</div>");
        b.append("<p>").append(StringUtil.escapeXmlEntities(doc.summary)).append("</p>");
        if (doc.detail != null && !doc.detail.isBlank()) {
            b.append("<p>").append(doc.detail).append("</p>");
        }
        b.append("<p><i>infra-go/applog</i></p>");
        return b.toString();
    }

    private static @Nullable ApplogKeyDoc describe(@NotNull String key) {
        if ("callerFileMaxLen".equals(key)) {
            return new ApplogKeyDoc(
                    "callerFileMaxLen",
                    "日志中 file:line 显示的最大宽度（超出左侧省略）。",
                    "对应 <code>yamlRoot.CallerFileMaxLen</code>，见 infra-go <code>applog/config.go</code>。"
            );
        }
        if ("appenders".equals(key)) {
            return new ApplogKeyDoc(
                    "appenders",
                    "具名 appender 定义表（console、rollingFile 等）。",
                    "在 <code>buildAppenders</code> 中按 <code>type</code> 实例化；<code>root</code> / <code>loggers.*</code> 通过名称引用。"
            );
        }
        if (key.startsWith("appenders.")) {
            String rest = key.substring("appenders.".length());
            int dot = rest.indexOf('.');
            String name = dot < 0 ? rest : rest.substring(0, dot);
            String sub = dot < 0 ? "" : rest.substring(dot + 1);
            if (sub.isBlank()) {
                return new ApplogKeyDoc(
                        "appenders." + name,
                        "具名 appender「" + name + "」。",
                        "字段含义见 <code>yamlAppender</code>：<code>type</code>（console | rollingFile）、<code>layout</code>、<code>pattern</code>、<code>path</code>（rollingFile）等。"
                );
            }
            return new ApplogKeyDoc(
                    key,
                    "appender「" + name + "」的子键「" + sub + "」。",
                    fieldDocAppender(sub)
            );
        }
        if ("root".equals(key) || key.startsWith("root.")) {
            return new ApplogKeyDoc(
                    key,
                    "根 logger（未命中命名 logger 时的默认）。",
                    "<code>level</code>、<code>appenders</code> 对应 <code>yamlLoggerDef</code>。"
            );
        }
        if ("loggers".equals(key)) {
            return new ApplogKeyDoc(
                    "loggers",
                    "命名 logger 表，与代码中 <code>applog.Get(name)</code> / <code>Name*</code> 常量一致。",
                    "常见键：<code>app</code>、<code>access</code>、<code>gorm</code>、<code>gin</code>。"
            );
        }
        if (key.startsWith("loggers.")) {
            String rest = key.substring("loggers.".length());
            int dot = rest.indexOf('.');
            String id = dot < 0 ? rest : rest.substring(0, dot);
            String sub = dot < 0 ? "" : rest.substring(dot + 1);
            String constHint = switch (id) {
                case "app" -> "<code>NameApp</code>（<code>logger.go</code>）";
                case "access" -> "<code>NameAccess</code>（<code>logger.go</code>）";
                case "gorm" -> "<code>NameGorm</code>（<code>gorm.go</code>）";
                case "gin" -> "<code>NameGinWriter</code>（<code>gin.go</code>）";
                case "root" -> "<code>NameRoot</code>（<code>logger.go</code>）";
                default -> "自定义命名；代码里使用相同字符串调用 applog.Get(\"" + StringUtil.escapeXmlEntities(id) + "\")。";
            };
            if (sub.isBlank()) {
                return new ApplogKeyDoc(
                        "loggers." + id,
                        "命名 logger「" + id + "」。",
                        "与 Go 侧约定：" + constHint + "。"
                );
            }
            return new ApplogKeyDoc(
                    key,
                    "logger「" + id + "」的配置项「" + sub + "」。",
                    fieldDocLogger(sub)
            );
        }
        return null;
    }

    private static @NotNull String fieldDocAppender(String sub) {
        return switch (sub) {
            case "type" -> "<code>console</code>：控制台；<code>rollingFile</code>：按行滚动文件（需 <code>path</code>）。";
            case "layout" -> "<code>text</code> | <code>pattern</code> | <code>json</code>。";
            case "pattern" -> "与 Spring 类似的 pattern，含 <code>%d</code>、<code>%level</code>、<code>%fileLine</code>、<code>%clr(){</code> 等。";
            case "path" -> "rollingFile 当前日志文件路径。";
            case "colored" -> "控制台是否 ANSI 着色。";
            case "maxLinesPerFile", "retentionDays" -> "rollingFile 单文件最大行数、历史文件保留天数。";
            default -> "见 <code>yamlAppender</code> 结构体（<code>applog/config.go</code>）。";
        };
    }

    private static @NotNull String fieldDocLogger(String sub) {
        return switch (sub) {
            case "level" -> "trace / debug / info / warn / error / fatal；控制该 logger 是否输出及最低级别。";
            case "appenders" -> "引用的 appender 名称列表，对应 <code>appenders.*</code> 下定义的键。";
            default -> "见 <code>yamlLoggerDef</code>（<code>applog/config.go</code>）。";
        };
    }

    private static final class ApplogKeyDoc {
        final String title;
        final String summary;
        final @Nullable String detail;

        ApplogKeyDoc(String title, String summary, @Nullable String detail) {
            this.title = title;
            this.summary = summary;
            this.detail = detail;
        }
    }
}
