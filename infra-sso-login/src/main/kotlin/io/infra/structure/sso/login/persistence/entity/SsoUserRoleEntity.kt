package io.infra.structure.sso.login.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Table

/** MyBatis-Flex 对账户与角色关联表的映射。 */
@Table("sso_user_role")
data class SsoUserRoleEntity(
    /** 关联 sso_user.id。 */
    @Column("user_id")
    var userId: Long? = null,
    /** 不带 ROLE_ 前缀的业务角色代码，例如 ORDER_READ。 */
    @Column("role_code")
    var roleCode: String = ""
)
