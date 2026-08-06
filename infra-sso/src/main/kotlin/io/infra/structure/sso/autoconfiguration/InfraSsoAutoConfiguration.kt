package io.infra.structure.sso.autoconfiguration

import io.infra.structure.sso.core.AudienceValidator
import io.infra.structure.sso.login.SsoLoginRedirectSupport
import io.infra.structure.sso.properties.SsoProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.InvalidCsrfTokenException
import org.springframework.security.web.csrf.MissingCsrfTokenException
import org.springframework.security.web.savedrequest.RequestCache
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.util.StringUtils

/**
 * infra-sso 自动配置。
 *
 * client.enabled=false 时创建无状态资源服务器安全链；client.enabled=true 时创建
 * 面向浏览器的 OAuth2 Client 安全链，同时保留 JWT Resource Server 能力。
 * 业务应用自行声明同类型 Bean 时，条件注解会让对应默认实现自动回退。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(SecurityFilterChain::class, JwtDecoder::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "infra.sso", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(SsoProperties::class)
class InfraSsoAutoConfiguration {

    /**
     * 创建 JWT 解码器，并组合签名、有效期、发行者及可选 audience 校验。
     *
     * 优先使用 issuer-uri 完成 OIDC Discovery；只有未配置 issuer-uri 时才直接使用 jwk-set-uri。
     */
    @Bean
    @ConditionalOnMissingBean
    fun infraSsoJwtDecoder(properties: SsoProperties): JwtDecoder {
        // issuer-uri 可同时完成 discovery 与 iss 校验；仅配置 JWK 时使用标准 JWT 校验器。
        val decoder: NimbusJwtDecoder = if (StringUtils.hasText(properties.issuerUri)) {
            JwtDecoders.fromIssuerLocation(properties.issuerUri)
        } else {
            NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri).build()
        }

        val defaultValidator: OAuth2TokenValidator<Jwt> = if (StringUtils.hasText(properties.issuerUri)) {
            JwtValidators.createDefaultWithIssuer(properties.issuerUri)
        } else {
            JwtValidators.createDefault()
        }
        decoder.setJwtValidator(
            if (StringUtils.hasText(properties.audience)) {
                DelegatingOAuth2TokenValidator(defaultValidator, AudienceValidator(properties.audience))
            } else {
                defaultValidator
            }
        )
        return decoder
    }

    /**
     * 创建 JWT 到 Spring Security Authentication 的权限转换器。
     *
     * 业务系统可通过 authorities-claim 和 authority-prefix 适配不同身份提供方的声明格式。
     */
    @Bean
    @ConditionalOnMissingBean
    fun infraSsoJwtAuthenticationConverter(properties: SsoProperties): JwtAuthenticationConverter {
        // 将 roles 等自定义声明映射为 Spring Security 可用于 hasAuthority 的 GrantedAuthority。
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName(properties.authoritiesClaim)
            setAuthorityPrefix(properties.authorityPrefix)
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
        }
    }

    /**
     * 为纯 API 应用创建无状态的资源服务器安全链。
     *
     * 此模式不会触发浏览器跳转，也不会创建 HTTP Session；未认证调用方必须提供 Bearer Token。
     */
    @Bean
    @ConditionalOnProperty(prefix = "infra.sso.client", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(SecurityFilterChain::class)
    fun infraSsoSecurityFilterChain(
        http: HttpSecurity,
        properties: SsoProperties,
        converter: JwtAuthenticationConverter
    ): SecurityFilterChain {
        // 纯 API 服务不保存会话，所有请求均通过 Bearer Token 认证。
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                properties.permitAll.forEach { pattern -> authorize.requestMatchers(pattern).permitAll() }
                authorize.anyRequest().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) }
            }
        return http.build()
    }

    /**
     * 仅缓存浏览器 HTML GET 请求，避免将 API 调用或任意回跳地址写入本地会话。
     * 认证成功后由成功处理器从该缓存恢复用户最初访问的页面。
     */
    @Bean
    @ConditionalOnProperty(prefix = "infra.sso.client", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean(RequestCache::class)
    fun infraSsoRequestCache(): RequestCache = SsoLoginRedirectSupport.requestCache()

    /**
     * 创建授权请求解析器，并在启用时为授权码流程附加 PKCE 参数。
     *
     * PKCE 的 verifier 保存在业务系统会话中，登录中心只接收 challenge 和后续 verifier。
     */
    @Bean
    @ConditionalOnProperty(prefix = "infra.sso.client", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean(OAuth2AuthorizationRequestResolver::class)
    fun infraSsoAuthorizationRequestResolver(
        properties: SsoProperties,
        clientRegistrationRepository: ClientRegistrationRepository
    ): OAuth2AuthorizationRequestResolver =
        DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization").apply {
            // PKCE verifier 由 Spring Security 保存到本地会话，换取授权码时自动携带。
            if (properties.client.isPkceEnabled) {
                setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
            }
        }

    /**
     * 创建 OIDC 发起的登出成功处理器。
     *
     * 它会将用户浏览器重定向到身份提供方的 end-session 端点，并携带已登记的业务回调地址。
     */
    @Bean
    @ConditionalOnProperty(prefix = "infra.sso.client", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean(LogoutSuccessHandler::class)
    fun infraSsoOidcLogoutSuccessHandler(
        properties: SsoProperties,
        clientRegistrationRepository: ClientRegistrationRepository
    ): LogoutSuccessHandler = OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository).apply {
        // 处理器会携带当前用户的 id_token_hint，并只允许配置的受信任回调地址。
        properties.client.postLogoutRedirectUri
            ?.takeIf(StringUtils::hasText)
            ?.let(::setPostLogoutRedirectUri)
    }

    /**
     * 为浏览器业务应用创建 OAuth2 Client 安全链。
     *
     * 受保护的 HTML 页面会进入登录中心，认证成功后再通过 SavedRequest 恢复原访问地址；
     * 同一条链也保留 Bearer JWT 资源服务器能力，方便页面应用提供受保护 API。
     */
    @Bean
    @ConditionalOnProperty(prefix = "infra.sso.client", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean(SecurityFilterChain::class)
    fun infraSsoClientSecurityFilterChain(
        http: HttpSecurity,
        properties: SsoProperties,
        converter: JwtAuthenticationConverter,
        requestCache: RequestCache,
        authorizationRequestResolver: OAuth2AuthorizationRequestResolver,
        logoutSuccessHandler: LogoutSuccessHandler
    ): SecurityFilterChain {
        // 未登录的 HTML 页面请求跳转到指定 client registration；非 HTML 请求保持默认处理方式。
        val authorizationEntryPoint = LoginUrlAuthenticationEntryPoint(
            "/oauth2/authorization/${properties.client.registrationId}"
        )
        http.authorizeHttpRequests { authorize ->
            properties.permitAll.forEach { pattern -> authorize.requestMatchers(pattern).permitAll() }
            authorize.anyRequest().authenticated()
        }
            // 会话过期后的旧退出表单不放行，只返回固定站内地址，避免将用户看到的 403 页面留在浏览器中。
            .csrf { csrf -> csrf.withObjectPostProcessor(logoutCsrfAccessDeniedPostProcessor("/")) }
            // SavedRequest 与 OAuth2 state 都保存在业务系统自己的会话中，不能与登录中心共用 Cookie。
            .requestCache { cache -> cache.requestCache(requestCache) }
            .exceptionHandling { exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    authorizationEntryPoint,
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            }
            .oauth2Login { login ->
                login.authorizationEndpoint { endpoint ->
                    endpoint.authorizationRequestResolver(authorizationRequestResolver)
                }
                login.successHandler(SsoLoginRedirectSupport.successHandler(requestCache))
            }
            // 先销毁业务系统会话，再按 OIDC 标准请求登录中心清理其会话。
            .logout { logout -> logout.logoutSuccessHandler(logoutSuccessHandler) }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) }
            }
        return http.build()
    }

    /**
     * 为 CsrfFilter 安装“过期会话退出”专用的拒绝处理器。
     *
     * CsrfConfigurer 没有直接暴露拒绝处理器配置，因此通过 ObjectPostProcessor 在过滤器创建后
     * 注入处理器；校验规则本身保持不变。
     */
    private fun logoutCsrfAccessDeniedPostProcessor(redirectTarget: String): ObjectPostProcessor<CsrfFilter> =
        object : ObjectPostProcessor<CsrfFilter> {
            override fun <O : CsrfFilter> postProcess(filter: O): O {
                filter.setAccessDeniedHandler(expiredSessionLogoutCsrfHandler(redirectTarget))
                return filter
            }
        }

    /**
     * 创建失效会话下退出请求的 CSRF 拒绝处理器。
     *
     * 只有服务端 Session 已不存在，且 POST /logout 出现 MissingCsrfTokenException 或
     * InvalidCsrfTokenException 时会重定向；其余请求继续由 Spring Security 返回 403，
     * 不能借此绕过 CSRF 校验。
     */
    private fun expiredSessionLogoutCsrfHandler(redirectTarget: String): AccessDeniedHandler {
        val defaultHandler = AccessDeniedHandlerImpl()
        return AccessDeniedHandler { request, response, exception ->
            val isLogoutRequest = request.method.equals(HttpMethod.POST.name(), ignoreCase = true) &&
                request.requestURI.removePrefix(request.contextPath) == "/logout"
            val isExpiredSessionCsrfFailure = exception is MissingCsrfTokenException || exception is InvalidCsrfTokenException
            if (request.getSession(false) == null && isLogoutRequest && isExpiredSessionCsrfFailure) {
                response.sendRedirect(request.contextPath + redirectTarget)
            } else {
                defaultHandler.handle(request, response, exception)
            }
        }
    }
}
