package io.infra.structure.sso.login.persistence

import com.mybatisflex.core.query.QueryWrapper
import io.infra.structure.sso.login.persistence.mapper.SsoUserMapper
import io.infra.structure.sso.login.persistence.mapper.SsoUserRoleMapper
import io.infra.structure.sso.login.persistence.model.SsoUserAccount
import org.springframework.stereotype.Repository

/**
 * 聚合 SSO 账户与角色的数据访问层。
 *
 * 使用 infra-db 提供的 MyBatis-Flex Mapper 查询单表，避免把认证逻辑耦合到 SQL 字符串。
 * 账户和角色分两次查询，保证一个没有角色的有效账号仍能完成登录。
 */
@Repository
class SsoUserAccountRepository(
    private val userMapper: SsoUserMapper,
    private val userRoleMapper: SsoUserRoleMapper
) {

    /** 根据唯一用户名读取账户，并同时加载已分配的角色代码。 */
    fun findByUsername(username: String): SsoUserAccount? {
        val user = userMapper.selectOneByQuery(
            QueryWrapper.create().eq("username", username)
        ) ?: return null
        // 数据库主键理论上非空；保守处理异常映射数据，避免构造不完整的认证主体。
        val userId = user.id ?: return null

        return SsoUserAccount(
            id = userId,
            username = user.username,
            email = user.email,
            passwordHash = user.passwordHash,
            enabled = user.enabled,
            roles = userRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .eq("user_id", userId)
                    .orderBy("role_code")
            ).orEmpty().mapNotNull { role -> role.roleCode.takeIf { it.isNotBlank() } }.toSet()
        )
    }
}
