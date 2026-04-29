# infra-go-spring-idea-plugin

`infra-go-spring-idea-plugin` 是一个独立的 IntelliJ IDEA 插件模块，用来补充 `go-spring` 的注入与配置导航能力。

当前实现支持：

- 在 Go 结构体 tag 的 `autowire:"beanName"`、`autowire:"a,*?,b"` 中跳转到 `Provide(...).Name("...")` 定义
- 在 Go 结构体 tag 的 `value:"${config.key}"`、`value:"${config.key:=default}"` 中跳转到 `app.properties` / `app.yml`
- 校验 Go 结构体 tag 中的 `gorm:"..."` 写法，语法不符合规范时直接标红
- 对 `gorm:"type:..."` 的值做额外校验，并提供常见 GORM/SQL 类型输入联想
- 从 `Provide(...).Name("beanName")` 反向跳转到所有匹配的 `autowire` 使用位置
- 从 `app.properties` / `app.yml` 的配置键反向跳转到所有匹配的 `value` 使用位置

当前限制：

- 重点支持文档中最常见的 `gs.Provide(...)` / `app.Provide(...)` + `.Name("...")` 命名 Bean 形式
- 当前只扫描 `app.properties`、`app.yml` 与 `app.yaml`
- 当前主要支持结构体字段 tag 场景，未覆盖所有 `gs.TagArg(...)` / 条件 API 的导航
- GORM 校验当前以 tag 语法规范为主，优先拦截空片段、缺少指令名、缺少值、未知指令等明显错误

构建方式：

```bash
../infra-idea-plugin/gradlew -p . buildPlugin
```
