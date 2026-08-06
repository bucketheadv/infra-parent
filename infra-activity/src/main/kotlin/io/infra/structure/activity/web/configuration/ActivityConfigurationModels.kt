package io.infra.structure.activity.web.configuration

import io.infra.structure.activity.domain.model.ComponentDefinition
import io.infra.structure.activity.domain.model.ComponentNodeType
import io.infra.structure.activity.domain.model.ComponentOption
import io.infra.structure.activity.domain.model.ComponentReferenceMode

/** 新建活动组件的请求。 */
data class CreateComponentRequest(
    /** 组件唯一编码。 */
    val code: String,
    /** 组件名称。 */
    val name: String,
    /** 组件说明。 */
    val description: String? = null,
    /** 组件内的递归字段定义。 */
    val definition: ComponentDefinition,
    /** 是否允许模板继续引用该组件。 */
    val enabled: Boolean = true
)

/** 新建活动模板的请求。 */
data class CreateTemplateRequest(
    /** 模板唯一编码。 */
    val code: String,
    /** 模板名称。 */
    val name: String,
    /** 模板说明。 */
    val description: String? = null,
    /** 模板直接挂载的普通输入字段定义。 */
    val definition: ComponentDefinition = ComponentDefinition(),
    /** 按页面顺序引用的组件。 */
    val components: List<TemplateComponentRequest> = emptyList(),
    /** 是否允许活动使用该模板。 */
    val enabled: Boolean = true
)

/** 模板内单个组件的引用配置。 */
data class TemplateComponentRequest(
    /** 被引用组件主键。 */
    val componentId: Long,
    /** 模板内唯一的手动挂载键，用于隔离组件实例的数据路径。 */
    val mountKey: String,
    /** 活动表单中展示该组件实例的标题。 */
    val mountTitle: String = "",
    /** 组件在模板中的挂载形式。 */
    val mountMode: ComponentReferenceMode = ComponentReferenceMode.SINGLE,
    /** 是否将组件内字段整体标记为必填。 */
    val required: Boolean = false,
    /** 可选的组件展示覆盖配置。 */
    val overrides: Map<String, Any?>? = null
)

/** 依据模板新建活动的请求。 */
data class CreateActivityRequest(
    /** 活动名称。 */
    val name: String,
    /** 采用的活动模板主键。 */
    val templateId: Long,
    /** 活动状态，支持 DRAFT 或 ACTIVE。 */
    val status: String = "DRAFT",
    /** 上下线状态，支持 ONLINE 或 OFFLINE。 */
    val onlineStatus: String = "OFFLINE",
    /** 是否永久有效；为 false 时必须填写开始和结束时间。 */
    val validForever: Boolean = true,
    /** 非永久活动的开始时间戳，单位为毫秒。 */
    val validStartTime: Long? = null,
    /** 非永久活动的结束时间戳，单位为毫秒。 */
    val validEndTime: Long? = null,
    /** 按模板组件、分组和字段层级组织的活动配置值。 */
    val values: Map<String, Any?> = emptyMap()
)

/** 单独更新活动上下线状态的请求。 */
data class UpdateActivityOnlineStatusRequest(
    /** 目标上下线状态，支持 ONLINE 或 OFFLINE。 */
    val onlineStatus: String
)

/** 单独更新活动调试配置的请求。 */
data class UpdateActivityDebugConfigurationRequest(
    /** 是否仅允许调试白名单中的用户访问活动。 */
    val debugMode: Boolean = false,
    /** 调试模式白名单中的用户主键；启用调试模式时至少填写一项。 */
    val debugUserIds: List<Long> = emptyList(),
    /** 调试模式强制使用的时间戳，单位为毫秒。 */
    val debugForceTime: Long? = null
)

/** 活动组件的页面和接口视图。 */
data class ActivityComponentResponse(
    /** 组件主键。 */
    val id: Long,
    /** 组件唯一编码。 */
    val code: String,
    /** 组件名称。 */
    val name: String,
    /** 组件说明。 */
    val description: String?,
    /** 递归字段定义。 */
    val definition: ComponentDefinition,
    /** 是否可用。 */
    val enabled: Boolean
)

/** 模板引用组件的详情。 */
data class TemplateComponentResponse(
    /** 关联记录主键。 */
    val id: Long,
    /** 展示顺序。 */
    val sortNo: Int,
    /** 模板内唯一的组件挂载键。 */
    val mountKey: String,
    /** 活动表单中展示该组件实例的标题。 */
    val mountTitle: String,
    /** 组件在模板中的挂载形式。 */
    val mountMode: ComponentReferenceMode,
    /** 是否将组件内字段整体标记为必填。 */
    val required: Boolean,
    /** 被引用组件详情。 */
    val component: ActivityComponentResponse
)

/** 活动模板的页面和接口视图。 */
data class ActivityTemplateResponse(
    /** 模板主键。 */
    val id: Long,
    /** 模板唯一编码。 */
    val code: String,
    /** 模板名称。 */
    val name: String,
    /** 模板说明。 */
    val description: String?,
    /** 是否可用。 */
    val enabled: Boolean,
    /** 模板直接挂载的普通输入字段定义。 */
    val definition: ComponentDefinition,
    /** 模板按顺序引用的组件。 */
    val components: List<TemplateComponentResponse>
)

/** 根据模板生成的单个可渲染活动字段。 */
data class ActivityFormField(
    /** 活动配置 JSON 使用的唯一字段键。 */
    val key: String,
    /** 页面展示名称。 */
    val label: String,
    /** 表单控件类型。 */
    val type: ComponentNodeType,
    /** 是否必须填写。 */
    val required: Boolean,
    /** 输入提示。 */
    val placeholder: String?,
    /** 动态表单初次渲染时填充的默认值。 */
    val defaultValue: String?,
    /** 下拉候选项。 */
    val options: List<ComponentOption>,
    /** 是否以数组形式渲染引用的子组件。 */
    val collection: Boolean = false,
    /** 当前分组或子组件下的嵌套字段。 */
    val children: List<ActivityFormField> = emptyList(),
    /** 用于页面缩进的层级深度。 */
    val depth: Int
)

/** 活动模板动态表单的接口响应。 */
data class ActivityFormResponse(
    /** 模板详情。 */
    val template: ActivityTemplateResponse,
    /** 保留组件层级关系的可编辑字段。 */
    val fields: List<ActivityFormField>
)

/** 已保存活动配置的接口响应。 */
data class ActivityResponse(
    /** 活动主键。 */
    val id: Long,
    /** 活动名称。 */
    val name: String,
    /** 模板主键。 */
    val templateId: Long,
    /** 活动状态。 */
    val status: String,
    /** 上下线状态。 */
    val onlineStatus: String,
    /** 是否永久有效。 */
    val validForever: Boolean,
    /** 非永久活动的开始时间戳，单位为毫秒。 */
    val validStartTime: Long?,
    /** 非永久活动的结束时间戳，单位为毫秒。 */
    val validEndTime: Long?,
    /** 是否启用仅面向白名单用户的调试模式。 */
    val debugMode: Boolean,
    /** 调试模式白名单中的用户主键。 */
    val debugUserIds: List<Long>,
    /** 调试模式强制使用的时间戳，单位为毫秒。 */
    val debugForceTime: Long?,
    /** 已保存的层级化动态表单值。 */
    val values: Map<String, Any?>
)
