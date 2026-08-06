# infra-activity

活动业务模块，演示如何组合 OAuth2 Client 登录跳转与 `infra-sso` JWT 校验。

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
