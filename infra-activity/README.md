# infra-activity

活动业务模块，提供基于 SSO 的活动配置管理能力，并演示如何组合 OAuth2 Client 登录跳转与 `infra-sso` JWT 校验。

## 包分层

- `io.infra.structure.activity.admin`：管理后台能力，包含活动组件、奖励组件、模板和活动配置的 Controller、Service、DTO，以及后台工作台页面。现有管理接口地址保持不变，仍为 `/api/activity/**`。
- `io.infra.structure.activity.frontend`：面向业务前端的活动构建能力。`BaseActivityDto`、`BaseActivityService` 与 `BaseActivityController` 都支持泛型；具体活动只暴露自身需要的类型化 DTO，不复用后台完整活动配置响应，也不将动态配置 `Map` 直接透传给前端。
- `frontend.type.ActivityType`：每个枚举项的 `templateCode` 必须与后台活动模板编码完全一致，并声明对应的表单数据类。新增枚举项时，必须同时新增对应的 DTO、表单数据类、Service、Controller，并分别继承三个基类。

当前提供 `LUCKY_DRAW("lucky_draw")` 示例实现，前端读取地址为：

```text
GET /api/activity/luckydraw/{activityId}
```

该接口只会构建模板编码匹配、模板已启用、活动已启用且上线、处于有效期内的活动；调试模式下还会校验当前 SSO 用户是否在白名单中。

## 活动配置

登录后可访问以下独立页面：`http://localhost:8081/activity/component`（模板组件配置）、`http://localhost:8081/activity/template`（活动模板配置）和 `http://localhost:8081/activity/config`（活动配置）。配置能力分为三个层级：

- 模板组件配置：定义可复用组件。一个组件可以包含文本、数字、日期、日期时间、单选下拉、多选下拉、多行文本、分组和已保存的子组件；子组件可选择单个对象或数组形式。日期时间控件支持精确到秒。下拉候选项只会在节点类型为单选或多选下拉时显示。
- 活动模板配置：按顺序挂载多个组件，也可直接配置普通输入项，形成活动的动态表单蓝图。
- 活动配置：选择模板后按组件定义自动渲染表单，校验必填项和下拉值后保存配置。

子组件数组在活动配置页可新增或移除实例，保存时每个实例按其索引保留字段路径。为避免无限递归，组件引用不能直接或间接引用自身。

活动模板可按顺序重复挂载同一个组件，但每次挂载必须手动设置模板内唯一的挂载键、展示用的挂载标题，并选择单个组件或组件数组。挂载键只允许小写字母、数字和下划线，并作为活动配置数据的根路径；挂载标题用于活动动态表单中该组件实例的分组标题。模板还可直接配置普通输入项。活动支持永久有效，或配置精确到秒的开始、结束时间；接口和数据库均以毫秒 `Long` 时间戳保存有效期。活动列表可复制现有活动，副本保留模板、有效期和配置值，并固定为草稿、下线状态。活动状态与上下线状态独立维护，上下线状态支持 `ONLINE` 和 `OFFLINE`。

## 数据库脚本

数据库脚本按用途分目录，所有生成脚本均使用 `yyyyMMddHHmmss_说明.sql` 的秒级日期时间整数前缀；按文件名升序即可得到升级执行顺序。

- `src/main/resources/db/mysql/schema/`：新数据库的完整建表脚本。
- `src/main/resources/db/mysql/migration/`：已有数据库的增量升级脚本，只执行尚未执行过的文件，并按文件名升序执行。
- `src/main/resources/db/mysql/seed/`：本地测试数据，可在建表后按需执行。

活动数据源默认是 `jdbc:mysql://localhost:3306/infra_activity`，可通过 `ACTIVITY_DB_URL`、`ACTIVITY_DB_USERNAME`、`ACTIVITY_DB_PASSWORD` 覆盖。新建数据库启动前执行：

```text
src/main/resources/db/mysql/schema/20260807090000_activity_schema.sql
src/main/resources/db/mysql/seed/20260807090100_activity_test_data.sql
```

演示数据会创建“基础信息”组件和“基础活动模板”，可直接用于创建第一个活动。

先启动登录模块，再启动本模块：

```bash
mvn -pl :infra-sso-login spring-boot:run
mvn -pl :infra-activity -am spring-boot:run
```

在浏览器中打开受保护页面：

```text
http://localhost:8081/
```

浏览器会自动跳转到登录模块。使用已创建的数据库账户登录后，OAuth2 Client 自动完成授权码换取，再跳回工作台。页面展示当前登录用户、权限与 SSO 会话状态，并提供退出登录入口。

本地演示中登录服务和活动模块都运行在 `localhost`，因此必须使用不同的 Session Cookie 名称。Cookie 不按端口隔离；若两端都使用默认的 `JSESSIONID`，登录服务会覆盖活动模块保存 OAuth2 `state` 与 PKCE verifier 的会话，导致回调落到 `/login?error`。

## 回调与原始页面恢复

业务系统和登录模块之间只使用一个预先注册、完全匹配的 OIDC 回调地址：

```text
http://localhost:8081/login/oauth2/code/infra-activity
```

`infra-sso` 自动配置会在业务系统本地 HTTP Session 中保存未登录用户访问的 HTML GET 请求。OAuth2 Client 生成不可预测的 `state` 并在回调时校验它；认证成功后，`SavedRequestAwareAuthenticationSuccessHandler` 从本地 Session 恢复原始 URL。原始 URL 不会作为任意 `returnUrl` 传给登录模块，因此不会形成开放重定向。

活动模块同时使用 PKCE。它会在授权请求发送 `code_challenge`，并仅在令牌交换时把相应的 `code_verifier` 发送给登录模块。

点击页面中的“退出登录”会先销毁活动模块的本地会话，再通过 OIDC RP-Initiated Logout 销毁登录中心会话，最后回到活动首页。登出回调地址必须预先登记在登录模块的 `post-logout-redirect-uris` 中。

跳转到登录模块的授权请求会携带上述 `redirect_uri`，且该地址已在 `infra-sso-login` 的 `clients.infra-activity.redirect-uris` 中注册。接入新的业务项目时，必须为该项目配置独立 client，并同时在登录模块和业务项目中使用完全相同的回调地址。

`/api/me` 同时支持由登录会话访问，以及使用 access token 的 API 调用：

```bash
curl -H "Authorization: Bearer ACCESS_TOKEN" http://localhost:8081/api/me
```

`/api/public/ping` 是无需令牌的健康检查示例。
