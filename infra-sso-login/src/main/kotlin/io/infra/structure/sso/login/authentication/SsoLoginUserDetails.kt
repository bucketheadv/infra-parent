package io.infra.structure.sso.login.authentication

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 登录中心认证成功后保存到 SecurityContext 的账户主体。
 *
 * 除 UserDetails 规定的用户名、密码和权限外，还保留数据库主键与邮箱，供令牌定制器
 * 签发稳定的 OIDC subject 及用户资料声明。
 */
class SsoLoginUserDetails(
    /** 数据库用户主键，也是对外签发的稳定 OIDC subject。 */
    val userId: Long,
    /** 用户邮箱，用于 OIDC email 声明。 */
    val email: String,
    private val loginName: String,
    private val passwordHash: String,
    private val grantedAuthorities: Collection<GrantedAuthority>,
    private val accountEnabled: Boolean
) : UserDetails {

    /** 返回当前账户已授予的 Spring Security 权限。 */
    override fun getAuthorities(): Collection<GrantedAuthority> = grantedAuthorities

    /** 返回委托编码后的密码哈希，供 PasswordEncoder 校验登录密码。 */
    override fun getPassword(): String = passwordHash

    /** 返回用户在登录表单中使用的账号名。 */
    override fun getUsername(): String = loginName

    /** 返回账户是否允许通过用户名和密码认证。 */
    override fun isEnabled(): Boolean = accountEnabled
}
