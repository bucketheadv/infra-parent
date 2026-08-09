package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 奖励模板与奖励组件关联表映射。 */
@Table("reward_template_component")
data class RewardTemplateComponentEntity(
    /** 关联记录主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 所属奖励模板主键。 */
    var templateId: Long = 0,
    /** 被引用奖励组件主键。 */
    var componentId: Long = 0,
    /** 奖励模板内唯一挂载键。 */
    var mountKey: String = "",
    /** 配置页面中的挂载标题。 */
    var mountTitle: String = "",
    /** 奖励组件在奖励模板中的挂载形式，SINGLE 或 ARRAY。 */
    var mountMode: String = "SINGLE",
    /** 展示顺序。 */
    var sortNo: Int = 0,
    /** 是否要求填写该组件。 */
    var required: Boolean = false
)
