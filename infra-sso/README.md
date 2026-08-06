# infra-sso 使用说明

`infra-sso` 是面向业务服务的 SSO 鉴权 starter，支持 OAuth 2.0 / OpenID Connect 的 Resource Server 与浏览器 OAuth2 Client 登录模式：

- 浏览器或客户端在统一身份提供商（IdP）完成登录；
- 调用业务服务时携带 `Authorization: Bearer <access-token>`；
- 本模块通过 IdP 的 issuer/JWK 公钥校验 JWT 的签名、有效期、issuer，并可校验 audience；
- 服务端不保存用户密码、会话或私钥，保持无状态，适合横向扩容。

## 引入依赖

```xml
<dependency>
    <groupId>io.infra.structure</groupId>
    <artifactId>infra-sso</artifactId>
</dependency>
```

## 最小配置

优先使用 OIDC issuer。模块会从该地址的 discovery endpoint 获取 JWK，并校验 `iss`：

```properties
infra.sso.enabled=true
infra.sso.issuer-uri=https://id.example.com/realms/platform
infra.sso.audience=order-service
infra.sso.authorities-claim=roles
infra.sso.authority-prefix=ROLE_
infra.sso.permit-all[0]=/error
infra.sso.permit-all[1]=/actuator/health
infra.sso.permit-all[2]=/api/public/**
```

若身份提供商没有 OIDC discovery endpoint，可改配其公开 JWK Set：

```properties
infra.sso.enabled=true
infra.sso.jwk-set-uri=https://id.example.com/oauth2/jwks
```

`issuer-uri` 和 `jwk-set-uri` 至少配置一个。配置 `issuer-uri` 时优先使用它。

## 控制器和服务中的鉴权

开启后，除 `permit-all` 外的所有请求都必须携带有效的 Bearer JWT。权限声明中的每个值会自动添加配置的前缀，因此 `roles: ["ORDER_READ"]` 会成为 `ROLE_ORDER_READ`。

```java
@PreAuthorize("hasAuthority('ROLE_ORDER_READ')")
@GetMapping("/orders")
public List<Order> list() {
    SsoUser user = SsoContext.requireCurrentUser();
    return orderService.listFor(user.getSubject());
}
```

使用 `@PreAuthorize` 时，请在业务应用自己的安全配置中启用 `@EnableMethodSecurity`。模块没有默认启用它，以免改变现有应用的方法鉴权策略。

```java
SsoUser user = SsoContext.currentUser();
if (user != null) {
    String userId = user.getSubject();
    String email = user.getEmail();
    Set<String> authorities = user.getAuthorities();
}
```

## 浏览器登录模式

浏览器访问业务页面、由统一登录中心认证并回到原始页面时，启用 `infra.sso.client`。模块会自动配置 Authorization Code + PKCE、保存原始 HTML GET 请求、OIDC RP-Initiated Logout 和 Resource Server JWT 校验，无需在业务项目中复制 `SecurityFilterChain`。

```yaml
infra:
  sso:
    enabled: true
    issuer-uri: http://localhost:9000
    audience: order-web
    permit-all:
      - /error
      - /assets/**
    client:
      enabled: true
      registration-id: order-web
      pkce-enabled: true
      post-logout-redirect-uri: "{baseUrl}/"

spring:
  security:
    oauth2:
      client:
        registration:
          order-web:
            client-id: order-web
            client-secret: ${ORDER_WEB_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: [openid, profile]
        provider:
          order-web:
            issuer-uri: http://localhost:9000
```

`registration-id` 必须与 `spring.security.oauth2.client.registration` 的名称一致。`post-logout-redirect-uri` 必须同时登记到登录中心该客户端的 `post-logout-redirect-uris`，否则登出请求会被拒绝。

## 自定义安全配置

如果业务应用已声明 `SecurityFilterChain`、`JwtDecoder` 或 `JwtAuthenticationConverter`，模块不会覆盖该 Bean；应用可在自定义配置中复用 `SsoProperties`，或完全接管安全策略。

浏览器登录模式下，业务应用可通过声明自己的 `SecurityFilterChain`、`RequestCache`、`OAuth2AuthorizationRequestResolver` 或 `LogoutSuccessHandler` Bean 来接管对应环节。未声明时，模块会使用 `SsoLoginRedirectSupport` 将未认证的 HTML GET 请求保存在本地 HTTP Session，并在 OIDC 回调成功后恢复原始深链：

```kotlin
val requestCache = SsoLoginRedirectSupport.requestCache()

http.requestCache { it.requestCache(requestCache) }
    .oauth2Login { it.successHandler(SsoLoginRedirectSupport.successHandler(requestCache)) }
```

这里保存的是业务系统本地的 `SavedRequest`，不是传给 IdP 的任意回跳 URL。IdP 侧仍只允许每个 client 注册的精确 `redirect_uri`；OAuth2 Client 会使用 Session 中的随机 `state` 完成 CSRF 校验。
