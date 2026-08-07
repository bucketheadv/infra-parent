package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动模板与奖励模板关联表映射。 */
@Table("activity_template_reward_template")
data class ActivityTemplateRewardTemplateEntity(
    /** 关联记录主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 所属活动模板主键。 */
    @Column("template_id")
    var templateId: Long = 0,
    /** 被引用奖励模板主键。 */
    @Column("reward_template_id")
    var rewardTemplateId: Long = 0,
    /** 活动模板内唯一挂载键。 */
    @Column("mount_key")
    var mountKey: String = "",
    /** 活动配置页面中的奖励挂载标题。 */
    @Column("mount_title")
    var mountTitle: String = "",
    /** 展示顺序。 */
    @Column("sort_no")
    var sortNo: Int = 0,
    /** 是否要求填写整个奖励模板。 */
    var required: Boolean = false
)
