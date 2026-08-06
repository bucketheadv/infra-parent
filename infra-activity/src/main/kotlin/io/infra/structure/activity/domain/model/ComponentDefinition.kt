package io.infra.structure.activity.domain.model

/**
 * 可复用活动组件的定义。
 *
 * 一个组件可包含多个字段节点，字段节点也可继续包含子节点，从而描述分组、重复区块等层级结构。
 */
data class ComponentDefinition(
    /** 组件内按展示顺序排列的根字段节点。 */
    val nodes: List<ComponentNode> = emptyList()
)

/** 活动表单中的单个字段或分组节点。 */
data class ComponentNode(
    /** 同一父节点内唯一的机器可读键，用于生成活动配置的键名。 */
    val key: String,
    /** 页面向运营人员展示的字段标题。 */
    val label: String,
    /** 节点的渲染类型。 */
    val type: ComponentNodeType,
    /** 是否要求在活动配置中填写有效值。 */
    val required: Boolean = false,
    /** 文本类输入框的提示内容。 */
    val placeholder: String? = null,
    /** 活动配置表单初次渲染时使用的默认值。 */
    val defaultValue: String? = null,
    /** 下拉选择节点可选的候选项。 */
    val options: List<ComponentOption> = emptyList(),
    /** 子组件节点引用的已保存组件主键。 */
    val componentId: Long? = null,
    /** 子组件节点在活动配置中的承载形式。 */
    val componentMode: ComponentReferenceMode = ComponentReferenceMode.SINGLE,
    /** 分组节点或复合节点包含的子字段。 */
    val children: List<ComponentNode> = emptyList()
)

/** 下拉选择节点的一项候选值。 */
data class ComponentOption(
    /** 保存到活动配置中的稳定值。 */
    val value: String,
    /** 页面展示给运营人员的文案。 */
    val label: String
)

/** 子组件在活动配置中的承载形式。 */
enum class ComponentReferenceMode {
    /** 以单个对象形式承载子组件。 */
    SINGLE,
    /** 以可新增多个实例的数组形式承载子组件。 */
    ARRAY
}

/** 支持的活动表单节点类型。 */
enum class ComponentNodeType {
    /** 单行文本输入框。 */
    TEXT,
    /** 多行文本输入框。 */
    TEXTAREA,
    /** 数字输入框。 */
    NUMBER,
    /** 日期输入框。 */
    DATE,
    /** 日期时间输入框。 */
    DATE_TIME,
    /** 引用另一个可复用组件作为子组件。 */
    COMPONENT,
    /** 下拉选择菜单。 */
    SELECT,
    /** 可同时选择多项候选值的下拉菜单。 */
    MULTI_SELECT,
    /** 仅用于承载子节点的分组。 */
    GROUP
}
