package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动模板主表的 MyBatis-Flex 映射。 */
@Table("activity_template")
data class ActivityTemplateEntity(
    /** 模板主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 模板唯一编码。 */
    var code: String = "",
    /** 模板展示名称。 */
    var name: String = "",
    /** 模板说明。 */
    var description: String? = null,
    /** 模板直接挂载的普通输入字段定义 JSON。 */
    var definitionJson: String? = null,
    /** 是否允许用于创建新活动。 */
    var enabled: Boolean = true,
    /** 创建时间戳，单位为毫秒。 */
    var createTime: Long? = null,
    /** 最后更新时间戳，单位为毫秒。 */
    var updateTime: Long? = null
)
