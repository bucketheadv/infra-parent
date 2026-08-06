package io.infra.structure.sso.login.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.sso.login.persistence.entity.SsoUserRoleEntity
import org.apache.ibatis.annotations.Mapper

/** sso_user_role 表的 MyBatis-Flex Mapper，用于读取账户已授予的角色。 */
@Mapper
interface SsoUserRoleMapper : BaseMapper<SsoUserRoleEntity>
