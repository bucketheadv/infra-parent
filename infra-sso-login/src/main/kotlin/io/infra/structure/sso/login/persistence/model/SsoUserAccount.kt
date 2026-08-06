package io.infra.structure.sso.login.persistence.model

/**
 * 认证层使用的账户聚合对象。
 *
 * 它与数据库实体分离，避免 Security 代码依赖 MyBatis-Flex 注解，也确保密码哈希
 * 只在认证流程内传递，不会被 Web 层序列化。
 */
data class SsoUserAccount(
    /** 数据库账户主键，用于查询该账户关联的角色。 */
    val id: Long,
    /** 用户提交登录表单时使用的账号名。 */
    val username: String,
    /** 用户邮箱，用于登录中心首页和 OIDC 用户资料声明。 */
    val email: String,
    /** 已采用委托编码格式保存的密码哈希，仅供 PasswordEncoder 比对。 */
    val passwordHash: String,
    /** 账户是否可用；不可用账户不能完成登录认证。 */
    val enabled: Boolean,
    /** 去重后的业务角色代码集合，不包含 Spring Security 的 ROLE_ 前缀。 */
    val roles: Set<String> = emptySet()
)
