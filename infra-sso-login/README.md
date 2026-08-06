# infra-sso-login

`infra-sso-login` 是可运行的 OAuth2/OIDC 身份提供方示例。它使用 Spring Authorization Server 提供登录页、授权端点、令牌端点、OIDC discovery 和 JWK Set。

```bash
mvn -pl :infra-sso-login spring-boot:run
```

登录账号从 MySQL 数据库读取，不再使用固定的 `username/password/roles` 配置。启动前设置数据源环境变量：`SSO_DB_URL`、`SSO_DB_USERNAME`、`SSO_DB_PASSWORD`。建表脚本位于 `src/main/resources/db/mysql/schema.sql`；已有旧表时先执行 `src/main/resources/db/mysql/upgrade-add-email.sql`。`password_hash` 必须使用 Spring Security 委托密码格式，例如 `{bcrypt}$2a$...`。角色写入 `sso_user_role.role_code`，例如 `ORDER_READ`，登录后会以 `ROLE_ORDER_READ` 授权并写入 access token 的 `roles` claim。仅供本地测试的 `demo / demo` 账号 DML 位于 `src/main/resources/db/mysql/test-data.sql`，邮箱为 `demo@example.com`。

认证成功后访问 `http://localhost:9000/` 可进入登录中心首页，查看当前账号与角色并退出登录。

`infra.sso.login.clients` 支持注册多个项目，每个项目配置独立的 `client-id`、`client-secret`、回调地址和 scopes。生产环境还应提供持久化的 `RegisteredClientRepository` 与 JWK。

业务项目配置的 `redirect-uri` 必须与这里登记的 `redirect-uris` 完全相同，例如：

```yaml
infra:
  sso:
    login:
      clients:
        order-web:
          client-id: order-web
          client-secret: ${ORDER_WEB_CLIENT_SECRET}
          redirect-uris:
            - https://order.example.com/login/oauth2/code/order-web
          require-proof-key: true
```

登录模块只根据已注册的 `redirect_uri` 回调业务系统。业务系统应将用户原始访问页面保存在自己的 Session，并在验证授权码与 `state` 后跳回该页面；`infra-sso` 提供的 `SsoLoginRedirectSupport` 已按该方式实现。

`require-proof-key: true` 表示该 client 的 Authorization Code Flow 必须使用 PKCE。SPA、移动端等 public client 应始终启用它，且不能保管 `client-secret`；保密 Web client 也推荐启用。

`http://localhost:9000/.well-known/openid-configuration` 提供 discovery 文档。登录后签发的 access token 包含 `roles` claim，能够被 `infra-sso` 的默认配置读取。
