package io.infra.structure.activity.web.model

/**
 * 当前用户资料接口的响应模型。
 *
 * 权限集合使用 Set 保证同一权限只返回一次，避免认证链中重复授予的权限影响调用方。
 */
data class ProfileResponse(
    /** 用户在身份提供方中的不可变唯一标识。 */
    val subject: String,
    /** 用户登录名；身份提供方未提供该声明时为 null。 */
    val username: String?,
    /** 用户邮箱；客户端未申请或身份提供方未提供时为 null。 */
    val email: String?,
    /** Spring Security 规范化后的权限集合。 */
    val authorities: Set<String>
)
