package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 奖励组件主表的 MyBatis-Flex 映射。 */
@Table("reward_component")
data class RewardComponentEntity(
    /** 奖励组件主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 奖励组件唯一编码。 */
    var code: String = "",
    /** 奖励组件展示名称。 */
    var name: String = "",
    /** 奖励组件用途说明。 */
    var description: String? = null,
    /** 奖励组件输入字段定义 JSON。 */
    @Column("definition_json")
    var definitionJson: String = "{}",
    /** 是否将奖品直接挂载到奖励模板，启用后不允许配置普通输入字段。 */
    @Column("direct_prize_mount")
    var directPrizeMount: Boolean = false,
    /** 是否允许被奖励模板继续引用。 */
    var enabled: Boolean = true,
    /** 创建时间戳，单位为毫秒。 */
    @Column("create_time")
    var createTime: Long? = null,
    /** 最后更新时间戳，单位为毫秒。 */
    @Column("update_time")
    var updateTime: Long? = null
)
