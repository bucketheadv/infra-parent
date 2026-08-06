package io.infra.structure.sso.login.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** MyBatis-Flex 对 sso_user 表的映射，仅承载账户认证所需字段。 */
@Table("sso_user")
data class SsoUserEntity(
    /** 数据库自增主键，用于关联角色表。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 登录时提交的唯一账号名。 */
    var username: String = "",
    /** 用户邮箱，会映射为 OIDC 的 email 声明。 */
    var email: String = "",
    /** 使用 {bcrypt} 等委托编码格式保存的密码哈希。 */
    @Column("password_hash")
    var passwordHash: String = "",
    /** false 时仍可查询到账号，但 Spring Security 会拒绝其认证。 */
    var enabled: Boolean = true
)
