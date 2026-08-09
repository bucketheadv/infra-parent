package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 奖励组件内奖品组件的关联表映射。 */
@Table("reward_component_prize")
data class RewardComponentPrizeEntity(
    /** 关联记录主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 所属奖励组件主键。 */
    @Column("component_id")
    var componentId: Long = 0,
    /** 采用的奖品组件主键，默认使用固定奖品组件 1。 */
    @Column("prize_component_id")
    var prizeComponentId: Long = 1,
    /** 奖励组件内唯一奖品挂载键。 */
    @Column("mount_key")
    var mountKey: String = "",
    /** 配置页面中的奖品挂载标题。 */
    @Column("mount_title")
    var mountTitle: String = "",
    /** 奖品组件在奖励组件中的挂载形式，SINGLE 或 ARRAY。 */
    @Column("mount_mode")
    var mountMode: String = "SINGLE",
    /** 奖品数组固定长度；为空表示允许活动配置自由增删。 */
    @Column("array_size")
    var arraySize: Int? = null,
    /** 展示顺序。 */
    @Column("sort_no")
    var sortNo: Int = 0,
    /** 是否要求填写该奖品组件。 */
    var required: Boolean = true
)
