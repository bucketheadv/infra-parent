package io.infra.structure.sso.core

import org.springframework.security.oauth2.jwt.Jwt

/** 已认证 SSO 用户的不可变视图，保留常用声明、权限和完整 claims。 */
data class SsoUser(
    /** 身份提供方签发的用户唯一标识，对应 JWT 的 sub 声明。 */
    val subject: String,
    /** 登录账号，对应可选的 preferred_username 声明。 */
    val username: String?,
    /** 用户展示名称，对应可选的 name 声明。 */
    val name: String?,
    /** 用户邮箱，对应可选的 email 声明。 */
    val email: String?,
    /** 已按 Spring Security 规则转换并去重后的权限集合。 */
    val authorities: Set<String>,
    /** JWT 的完整声明副本，供业务按需读取非标准字段。 */
    val claims: Map<String, Any>
) {
    companion object {
        /**
         * 从已完成签名、过期时间及发行者校验的 JWT 构造用户视图。
         *
         * authorities 由 JwtAuthenticationConverter 根据配置中的声明名称和前缀转换，
         * 因而不能直接使用原始 JWT 的 roles 声明替代。
         */
        fun from(jwt: Jwt, authorities: Set<String>): SsoUser = SsoUser(
            subject = jwt.subject,
            username = jwt.getClaimAsString("preferred_username"),
            name = jwt.getClaimAsString("name"),
            email = jwt.getClaimAsString("email"),
            authorities = authorities,
            claims = jwt.claims.toMap()
        )
    }
}
