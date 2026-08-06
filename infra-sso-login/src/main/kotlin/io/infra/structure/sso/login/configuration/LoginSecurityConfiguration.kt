package io.infra.structure.sso.login.configuration

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import io.infra.structure.sso.login.authentication.SsoLoginUserDetails
import io.infra.structure.sso.login.properties.SsoLoginProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.http.MediaType
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

/**
 * 登录中心的 Spring Authorization Server 配置。
 *
 * 第一条安全链只处理授权、令牌、JWK 与 OIDC 登出等协议端点；第二条安全链处理
 * 登录页、首页和静态资源。两条链必须保持顺序，避免协议端点落入普通表单登录链。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SsoLoginProperties::class)
class LoginSecurityConfiguration {

    /** 配置 OAuth2/OIDC 协议端点，并让浏览器未认证请求转入自定义登录页。 */
    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val authorizationServer = OAuth2AuthorizationServerConfigurer()
        http.securityMatcher(authorizationServer.endpointsMatcher)
            .with(authorizationServer) { server -> server.oidc(Customizer.withDefaults()) }
            .authorizeHttpRequests { authorize -> authorize.anyRequest().authenticated() }
            .exceptionHandling { exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint("/login"),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            }
        return http.build()
    }

    /**
     * 配置登录中心自身的页面访问规则。
     *
     * 登录页和静态资源允许匿名访问；其余页面必须先完成认证。退出登录后跳回
     * 登录页而非首页，避免退出后继续看到原有身份信息。
     */
    @Bean
    @Order(2)
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests { authorize ->
            authorize
                .requestMatchers("/login", "/sso-login/**").permitAll()
                .anyRequest().authenticated()
        }.formLogin { form ->
            form.loginPage("/login").permitAll()
        }.logout { logout ->
            logout.logoutSuccessUrl("/login?logout")
        }
        return http.build()
    }

    /**
     * 使用 Spring Security 委托密码编码器。
     *
     * 数据库账户密码和 OAuth 客户端密钥都以 {bcrypt} 等带算法标识的格式保存，
     * 可避免不同认证组件使用不同编码器时出现凭据校验失败。
     */
    @Bean
    @ConditionalOnMissingBean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /**
     * 根据配置创建内存客户端注册表，适用于本地演示和小规模部署。
     *
     * 生产环境可自行声明 RegisteredClientRepository Bean 接管此实现，并使用数据库
     * 持久化客户端、精确回调地址与登出回调地址。
     */
    @Bean
    @ConditionalOnMissingBean(RegisteredClientRepository::class)
    fun registeredClientRepository(properties: SsoLoginProperties, encoder: PasswordEncoder): RegisteredClientRepository =
        InMemoryRegisteredClientRepository(properties.clients.values.map { client ->
            RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(client.clientId)
                .clientSecret(encoder.encode(client.clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris { uris -> uris.addAll(client.redirectUris) }
                .postLogoutRedirectUris { uris -> uris.addAll(client.postLogoutRedirectUris) }
                .scopes { scopes -> scopes.addAll(client.scopes) }
                .clientSettings(
                    ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(client.isRequireProofKey)
                        .build()
                )
                .build()
        })

    /**
     * 生成用于签发 JWT 的临时 RSA 密钥。
     *
     * 默认密钥随进程重启变化，已签发 token 会失效；生产环境应替换为持久化的
     * JWKSource，并通过密钥轮换策略维护 kid。
     */
    @Bean
    @ConditionalOnMissingBean
    fun jwkSource(): JWKSource<SecurityContext> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(UUID.randomUUID().toString())
            .build()
        return ImmutableJWKSet(JWKSet(rsaKey))
    }

    /** 为 UserInfo 与协议组件提供 JWT 解码器，解码公钥来自当前 JWKSource。 */
    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)

    /** 指定 OIDC discovery 中发布的 issuer，必须与外部访问登录中心的地址一致。 */
    @Bean
    fun authorizationServerSettings(properties: SsoLoginProperties): AuthorizationServerSettings =
        AuthorizationServerSettings.builder().issuer(properties.issuer).build()

    /**
     * 为令牌补充登录账户的稳定标识、基础资料和业务角色。
     *
     * 数据库角色在 UserDetails 中以 ROLE_ 前缀保存；对外 access token 仅暴露原始角色代码，
     * 资源服务器会再根据 infra.sso.authority-prefix 映射为 GrantedAuthority。ID Token 中的
     * subject 使用数据库主键，保证用户修改登录名后身份标识仍然稳定。
     */
    @Bean
    fun jwtTokenCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> = OAuth2TokenCustomizer { context ->
        val principal = context.getPrincipal<Authentication>().principal as? SsoLoginUserDetails ?: return@OAuth2TokenCustomizer
        context.claims.subject(principal.userId.toString())

        if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN) {
            val roles = context.getPrincipal<Authentication>().authorities.mapNotNull { authority ->
                authority.authority?.removePrefix("ROLE_")
            }
            context.claims.claim("roles", roles)
            context.claims.audience(listOf(context.registeredClient.clientId))
        }

        if (context.tokenType.value == OidcParameterNames.ID_TOKEN) {
            if (context.authorizedScopes.contains("profile")) {
                context.claims.claim("preferred_username", principal.username)
                context.claims.claim("name", principal.username)
            }
            if (context.authorizedScopes.contains("email")) {
                context.claims.claim("email", principal.email)
                context.claims.claim("email_verified", true)
            }
        }
    }
}
