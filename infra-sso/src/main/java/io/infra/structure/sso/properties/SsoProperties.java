package io.infra.structure.sso.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * infra-sso 的统一配置。
 *
 * 同时覆盖纯资源服务器模式与浏览器 OAuth2 Client 登录模式：前者仅校验 Bearer Token，
 * 后者会维护本地会话以完成授权码、state、PKCE verifier 与原始访问地址的校验。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "infra.sso")
public class SsoProperties {

    /** 是否启用 infra-sso 自动配置。未启用时模块不会创建安全相关 Bean。 */
    private boolean enabled = false;

    /**
     * OpenID Connect 发行方地址。优先使用该配置，模块会通过 discovery 获取 JWK，
     * 并同时校验 access token 的 iss 声明。
     */
    private String issuerUri;

    /** 不支持 OIDC discovery 的身份提供方可直接配置公开 JWK Set 地址。 */
    private String jwkSetUri;

    /** access token 必须包含的 audience；为空时不额外限制 audience。 */
    private String audience;

    /** JWT 中承载角色或权限列表的声明名。 */
    @NotBlank
    private String authoritiesClaim = "roles";

    /** 角色或权限映射为 Spring Security GrantedAuthority 时添加的前缀。 */
    private String authorityPrefix = "ROLE_";

    /** 无需认证的路径白名单，例如错误页、健康检查及前端静态资源。 */
    private List<String> permitAll = new ArrayList<>(List.of("/error", "/actuator/health"));

    /** 面向浏览器页面的 OAuth2 Client 登录配置。 */
    private Client client = new Client();

    @AssertTrue(message = "必须配置 infra.sso.issuer-uri 或 infra.sso.jwk-set-uri")
    public boolean isTokenSourceConfigured() {
        return hasText(issuerUri) || hasText(jwkSetUri);
    }

    @AssertTrue(message = "启用浏览器登录时必须配置 infra.sso.client.registration-id")
    public boolean isClientRegistrationConfigured() {
        return !client.enabled || hasText(client.registrationId);
    }

    @Data
    public static class Client {

        /**
         * 是否启用浏览器登录模式。启用后自动配置 Authorization Code、PKCE、
         * 本地 SavedRequest 恢复和 OIDC RP-Initiated Logout。
         */
        private boolean enabled = false;

        /**
         * Spring Security OAuth2 Client 注册名，必须与
         * spring.security.oauth2.client.registration 下的配置键完全一致。
         */
        private String registrationId;

        /** 是否在授权请求中附带 RFC 7636 PKCE challenge；保密客户端也建议保持启用。 */
        private boolean pkceEnabled = true;

        /**
         * OIDC RP-Initiated Logout 完成后跳回业务系统的受信任地址模板。
         * 最终地址必须在登录中心登记为 post-logout-redirect-uri。
         */
        private String postLogoutRedirectUri = "{baseUrl}/";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
