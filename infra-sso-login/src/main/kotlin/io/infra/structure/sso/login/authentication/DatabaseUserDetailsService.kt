package io.infra.structure.sso.login.authentication

import io.infra.structure.sso.login.persistence.SsoUserAccountRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * 基于数据库账户的 UserDetailsService。
 *
 * Spring Security 的表单登录会调用此服务查询账号。密码哈希不在此处重新编码，
 * 而是直接交给 PasswordEncoder 与用户输入密码比对，避免破坏 BCrypt 的随机盐。
 */
@Service
class DatabaseUserDetailsService(
    private val userAccountRepository: SsoUserAccountRepository
) : UserDetailsService {

    /** 将数据库账户与角色转换为 Spring Security 登录用户。 */
    override fun loadUserByUsername(username: String): UserDetails {
        val account = userAccountRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found")

        return SsoLoginUserDetails(
            userId = account.id,
            email = account.email,
            loginName = account.username,
            passwordHash = account.passwordHash,
            grantedAuthorities = account.roles.map(::roleAuthority),
            accountEnabled = account.enabled
        )
    }

    /** 兼容数据库中是否已存储 ROLE_ 前缀两种形式。 */
    private fun roleAuthority(role: String): SimpleGrantedAuthority =
        SimpleGrantedAuthority(if (role.startsWith("ROLE_")) role else "ROLE_$role")
}
