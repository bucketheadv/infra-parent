package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动组件主表的 MyBatis-Flex 映射。 */
@Table("activity_component")
data class ActivityComponentEntity(
    /** 组件主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 组件唯一编码，供模板和接口稳定引用。 */
    var code: String = "",
    /** 组件展示名称。 */
    var name: String = "",
    /** 组件用途说明。 */
    var description: String? = null,
    /** 递归字段定义的 JSON 文本。 */
    var definitionJson: String = "{}",
    /** 是否允许被新模板继续引用。 */
    var enabled: Boolean = true,
    /** 创建时间戳，单位为毫秒。 */
    var createTime: Long? = null,
    /** 最后更新时间戳，单位为毫秒。 */
    var updateTime: Long? = null
)
