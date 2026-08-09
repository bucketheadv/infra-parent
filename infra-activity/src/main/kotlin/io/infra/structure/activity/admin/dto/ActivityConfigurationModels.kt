package io.infra.structure.activity.admin.dto

import io.infra.structure.activity.admin.domain.model.ComponentDefinition
import io.infra.structure.activity.admin.domain.model.ComponentNodeType
import io.infra.structure.activity.admin.domain.model.ComponentOption
import io.infra.structure.activity.admin.domain.model.ComponentReferenceMode

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
    /** 按页面顺序挂载的奖励模板。 */
    val rewardTemplates: List<TemplateRewardTemplateRequest> = emptyList(),
    /** 是否允许活动使用该模板。 */
    val enabled: Boolean = true
)

/** 新建奖励组件的请求。 */
data class CreateRewardComponentRequest(
    /** 奖励组件唯一编码。 */
    val code: String,
    /** 奖励组件名称。 */
    val name: String,
    /** 奖励组件说明。 */
    val description: String? = null,
    /** 奖励组件内的输入字段定义。 */
    val definition: ComponentDefinition,
    /** 奖励组件内挂载的固定奖品组件。 */
    val prizes: List<RewardComponentPrizeRequest> = emptyList(),
    /** 是否允许奖励模板继续引用该组件。 */
    val enabled: Boolean = true
)

/** 新建奖励模板的请求。 */
data class CreateRewardTemplateRequest(
    /** 奖励模板唯一编码。 */
    val code: String,
    /** 奖励模板名称。 */
    val name: String,
    /** 奖励模板说明。 */
    val description: String? = null,
    /** 奖励模板挂载的奖励组件。 */
    val components: List<RewardTemplateComponentRequest> = emptyList(),
    /** 是否允许活动模板继续引用该模板。 */
    val enabled: Boolean = true
)

/** 奖励模板内单个奖励组件的引用配置。 */
data class RewardTemplateComponentRequest(
    /** 被引用奖励组件主键。 */
    val componentId: Long,
    /** 奖励模板内唯一挂载键。 */
    val mountKey: String,
    /** 配置页面中展示的挂载标题。 */
    val mountTitle: String = "",
    /** 奖励组件在奖励模板中的挂载形式。 */
    val mountMode: ComponentReferenceMode = ComponentReferenceMode.SINGLE,
    /** 是否将组件内字段整体标记为必填。 */
    val required: Boolean = false
)

/** 奖励组件内固定奖品组件的配置。 */
data class RewardComponentPrizeRequest(
    /** 奖励组件内唯一奖品挂载键。 */
    val mountKey: String,
    /** 配置页面中展示的奖品标题。 */
    val mountTitle: String = "",
    /** 奖品组件在奖励组件中的挂载形式。 */
    val mountMode: ComponentReferenceMode = ComponentReferenceMode.SINGLE,
    /** 奖品数组的固定长度；未设置时可由运营人员自由增删。 */
    val arraySize: Int? = null,
    /** 是否要求填写该奖品。 */
    val required: Boolean = true
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

/** 活动模板内单个奖励模板的引用配置。 */
data class TemplateRewardTemplateRequest(
    /** 被引用奖励模板主键。 */
    val rewardTemplateId: Long,
    /** 活动模板内唯一奖励挂载键。 */
    val mountKey: String,
    /** 活动配置页面中展示的奖励标题。 */
    val mountTitle: String = "",
    /** 是否要求填写整个奖励模板。 */
    val required: Boolean = false
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

/** 奖励组件的页面和接口视图。 */
data class RewardComponentResponse(
    /** 奖励组件主键。 */
    val id: Long,
    /** 奖励组件唯一编码。 */
    val code: String,
    /** 奖励组件名称。 */
    val name: String,
    /** 奖励组件说明。 */
    val description: String?,
    /** 输入字段定义。 */
    val definition: ComponentDefinition,
    /** 固定奖品组件编排。 */
    val prizes: List<RewardComponentPrizeResponse>,
    /** 是否可用。 */
    val enabled: Boolean
)

/** 奖励模板引用奖励组件的详情。 */
data class RewardTemplateComponentResponse(
    /** 关联记录主键。 */
    val id: Long,
    /** 展示顺序。 */
    val sortNo: Int,
    /** 奖励模板内唯一挂载键。 */
    val mountKey: String,
    /** 配置页面中展示的挂载标题。 */
    val mountTitle: String,
    /** 奖励组件在奖励模板中的挂载形式。 */
    val mountMode: ComponentReferenceMode,
    /** 是否要求填写。 */
    val required: Boolean,
    /** 被引用奖励组件详情。 */
    val component: RewardComponentResponse
)

/** 奖励组件内固定奖品组件的详情。 */
data class RewardComponentPrizeResponse(
    /** 关联记录主键。 */
    val id: Long,
    /** 展示顺序。 */
    val sortNo: Int,
    /** 奖品挂载键。 */
    val mountKey: String,
    /** 奖品挂载标题。 */
    val mountTitle: String,
    /** 奖品组件在奖励组件中的挂载形式。 */
    val mountMode: ComponentReferenceMode,
    /** 奖品数组的固定长度；为空表示数量不受限制。 */
    val arraySize: Int?,
    /** 是否要求填写。 */
    val required: Boolean
)

/** 奖励模板的页面和接口视图。 */
data class RewardTemplateResponse(
    /** 奖励模板主键。 */
    val id: Long,
    /** 奖励模板唯一编码。 */
    val code: String,
    /** 奖励模板名称。 */
    val name: String,
    /** 奖励模板说明。 */
    val description: String?,
    /** 是否可用。 */
    val enabled: Boolean,
    /** 奖励组件编排。 */
    val components: List<RewardTemplateComponentResponse>
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

/** 活动模板引用奖励模板的详情。 */
data class TemplateRewardTemplateResponse(
    /** 关联记录主键。 */
    val id: Long,
    /** 展示顺序。 */
    val sortNo: Int,
    /** 活动模板内唯一奖励挂载键。 */
    val mountKey: String,
    /** 活动配置页面中展示的奖励标题。 */
    val mountTitle: String,
    /** 是否要求填写。 */
    val required: Boolean,
    /** 被引用奖励模板详情。 */
    val rewardTemplate: RewardTemplateResponse
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
    val components: List<TemplateComponentResponse>,
    /** 模板按顺序引用的奖励模板。 */
    val rewardTemplates: List<TemplateRewardTemplateResponse> = emptyList()
)

/** 奖品中心查询到的可编辑奖品属性。 */
data class PrizeLookupResponse(
    /** 奖品类型。 */
    val prizeType: String,
    /** 奖品唯一标识。 */
    val prizeId: String,
    /** 奖品名称。 */
    val prizeName: String,
    /** 奖品图标地址。 */
    val prizeIcon: String,
    /** 奖品价值。 */
    val prizeValue: String,
    /** 面向用户展示的奖品价值文案。 */
    val prizeDisplayValue: String,
    /** 建议的奖品数量。 */
    val prizeQuantity: String
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
    /** 数组字段要求固定填写的项目数；为空表示允许自由增删。 */
    val collectionSize: Int? = null,
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

/** 已通过前端可见性校验的活动配置传输模型。 */
data class FrontendActivityResponse(
    /** 活动主键。 */
    val id: Long,
    /** 活动展示名称。 */
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
    /** 活动表单配置原始 JSON，由前端活动服务解析为具体数据类型。 */
    val formDataJson: String,
    /** 创建时间戳，单位为毫秒。 */
    val createTime: Long,
    /** 最后更新时间戳，单位为毫秒。 */
    val updateTime: Long
)
