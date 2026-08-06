package io.infra.structure.sso.login.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录中心的运行参数。
 *
 * <p>该类只描述授权服务器自身的公开地址及可信 OAuth2 客户端。每个客户端的回调地址和
 * 登出回调地址都必须明确配置，不能使用通配符，以便授权服务器在重定向前进行严格校验。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "infra.sso.login")
public class SsoLoginProperties {

    /**
     * 授权服务器对外公布的发行者地址。
     *
     * <p>该地址会写入 JWT 的 {@code iss} 声明，并用于生成 OIDC Discovery 文档中的端点地址；
     * 必须与客户端配置的 provider issuer 保持完全一致。</p>
     */
    @NotBlank
    private String issuer;

    /**
     * 以业务名称为键的可信客户端集合。
     *
     * <p>键仅用于配置和日志定位，真正参与 OAuth2 协议的是每项中的 {@code clientId}。</p>
     */
    @NotEmpty
    private Map<String, Client> clients = new LinkedHashMap<>();

    /** 单个 OAuth2/OIDC 客户端的注册信息。 */
    @Data
    public static class Client {

        /**
         * 客户端在授权服务器中的唯一标识。
         *
         * <p>业务系统中 {@code spring.security.oauth2.client.registration} 的 client-id
         * 必须使用相同的值。</p>
         */
        @NotBlank
        private String clientId;

        /**
         * 客户端密钥。
         *
         * <p>该值会被 PasswordEncoder 编码后保存到内存注册表中，配置文件应通过环境变量或
         * 密钥管理服务注入，避免提交明文密钥。</p>
         */
        @NotBlank
        private String clientSecret;

        /**
         * 登录授权成功后允许跳转的精确回调地址集合。
         *
         * <p>地址必须与客户端发起授权请求时的 {@code redirect_uri} 完全匹配，防止授权码
         * 被重定向到非受信任站点。</p>
         */
        @NotEmpty
        private List<String> redirectUris = new ArrayList<>();

        /**
         * OIDC 登出完成后允许跳转的精确回调地址集合。
         *
         * <p>业务系统的 {@code infra.sso.client.post-logout-redirect-uri} 应在此处登记；
         * 未登记的地址会被授权服务器拒绝。</p>
         */
        private List<String> postLogoutRedirectUris = new ArrayList<>();

        /**
         * 客户端可申请的授权范围。
         *
         * <p>默认提供 {@code openid} 和 {@code profile}，其中 {@code openid} 是启用 OIDC
         * 登录的必要范围。</p>
         */
        private List<String> scopes = new ArrayList<>(List.of("openid", "profile"));

        /**
         * 是否要求授权码换取令牌时携带 RFC 7636 PKCE verifier。
         *
         * <p>浏览器、移动端等无法可靠保管 client secret 的客户端应保持启用；业务系统需同时
         * 设置 {@code infra.sso.client.pkce-enabled=true}，由 Spring Security 自动生成并保存 verifier。</p>
         */
        private boolean requireProofKey = false;
    }
}
