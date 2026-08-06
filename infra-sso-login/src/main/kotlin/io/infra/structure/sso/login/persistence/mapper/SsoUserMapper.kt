package io.infra.structure.sso.login.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.sso.login.persistence.entity.SsoUserEntity
import org.apache.ibatis.annotations.Mapper

/** sso_user 表的 MyBatis-Flex Mapper，由框架自动生成基础 CRUD 与条件查询。 */
@Mapper
interface SsoUserMapper : BaseMapper<SsoUserEntity>
