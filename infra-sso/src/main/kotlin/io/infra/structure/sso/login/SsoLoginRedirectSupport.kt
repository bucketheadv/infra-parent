package io.infra.structure.sso.login

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.web.RedirectStrategy
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.RequestCache
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * 浏览器 OAuth2 Client 的原始页面恢复工具。
 *
 * 用户访问业务系统受保护页面时，原始地址只保存在业务系统自己的 HTTP Session 中；
 * 不会作为任意 returnUrl 传给登录中心。跨系统只使用预先注册的精确 OIDC redirect_uri，
 * 从而避免开放重定向风险。
 */
object SsoLoginRedirectSupport {

    /**
     * 创建仅匹配浏览器 HTML GET 导航的请求匹配器。
     *
     * 登录成功后只有此类请求会被恢复，表单提交、接口调用和其他非页面请求不会被缓存。
     */
    fun htmlNavigationRequestMatcher(): RequestMatcher {
        // 只缓存浏览器页面跳转，避免 API 请求在认证完成后被错误地重放。
        val htmlRequest = MediaTypeRequestMatcher(MediaType.TEXT_HTML)
        return RequestMatcher { request ->
            request.method.equals(HttpMethod.GET.name(), ignoreCase = true) && htmlRequest.matches(request)
        }
    }

    /**
     * 创建基于业务系统 HTTP Session 的原始请求缓存。
     *
     * 默认仅保存 HTML 页面导航，调用方也可传入更严格的匹配规则。
     */
    fun requestCache(requestMatcher: RequestMatcher = htmlNavigationRequestMatcher()): HttpSessionRequestCache =
        HttpSessionRequestCache().apply {
            setRequestMatcher(requestMatcher)
        }

    /**
     * 创建认证成功处理器并恢复认证前缓存的页面地址。
     *
     * redirectStrategy 仅用于测试或业务系统确有自定义重定向策略时覆盖默认实现。
     */
    fun successHandler(
        requestCache: RequestCache,
        redirectStrategy: RedirectStrategy? = null
    ): AuthenticationSuccessHandler = SavedRequestAwareAuthenticationSuccessHandler().apply {
        // Spring Security 负责从业务系统 Session 读取 SavedRequest 并写入重定向响应。
        setRequestCache(requestCache)
        redirectStrategy?.let(::setRedirectStrategy)
    }.let { delegate ->
        ConsumingSavedRequestSuccessHandler(requestCache, delegate)
    }

    /**
     * 在写入重定向响应后主动清除 SavedRequest 的处理器。
     *
     * 避免用户后续再次认证时意外跳转到已经消费过的历史页面。
     */
    private class ConsumingSavedRequestSuccessHandler(
        private val requestCache: RequestCache,
        private val delegate: SavedRequestAwareAuthenticationSuccessHandler
    ) : AuthenticationSuccessHandler {

        /** 执行默认成功跳转，并在目标地址写入响应后清理原始请求缓存。 */
        override fun onAuthenticationSuccess(
            request: HttpServletRequest,
            response: HttpServletResponse,
            authentication: Authentication
        ) {
            delegate.onAuthenticationSuccess(request, response, authentication)
            // 委托处理器已读取并写入目标地址，此时移除缓存以避免后续请求重复跳转。
            requestCache.removeRequest(request, response)
        }
    }
}
