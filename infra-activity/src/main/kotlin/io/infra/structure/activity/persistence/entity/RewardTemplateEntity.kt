package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 奖励模板主表的 MyBatis-Flex 映射。 */
@Table("reward_template")
data class RewardTemplateEntity(
    /** 奖励模板主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 奖励模板唯一编码。 */
    var code: String = "",
    /** 奖励模板展示名称。 */
    var name: String = "",
    /** 奖励模板用途说明。 */
    var description: String? = null,
    /** 是否允许被活动模板继续引用。 */
    var enabled: Boolean = true,
    /** 创建时间戳，单位为毫秒。 */
    var createTime: Long? = null,
    /** 最后更新时间戳，单位为毫秒。 */
    var updateTime: Long? = null
)
