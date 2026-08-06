package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动模板与组件关联表的 MyBatis-Flex 映射。 */
@Table("activity_template_component")
data class ActivityTemplateComponentEntity(
    /** 关联记录主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 所属活动模板主键。 */
    @Column("template_id")
    var templateId: Long = 0,
    /** 被模板引用的组件主键。 */
    @Column("component_id")
    var componentId: Long = 0,
    /** 模板内唯一的组件挂载键。 */
    @Column("mount_key")
    var mountKey: String = "",
    /** 活动表单中展示该组件实例的标题。 */
    @Column("mount_title")
    var mountTitle: String = "",
    /** 组件在模板中的挂载形式，取值为 SINGLE 或 ARRAY。 */
    @Column("mount_mode")
    var mountMode: String = "SINGLE",
    /** 组件在模板页面中的展示顺序。 */
    @Column("sort_no")
    var sortNo: Int = 0,
    /** 模板是否要求活动必须填写该组件中的必填字段。 */
    var required: Boolean = false,
    /** 为模板保留的组件展示覆盖配置 JSON。 */
    @Column("overrides_json")
    var overridesJson: String? = null
)
