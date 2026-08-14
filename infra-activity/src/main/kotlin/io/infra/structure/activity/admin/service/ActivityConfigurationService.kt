package io.infra.structure.activity.admin.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.mybatisflex.core.query.QueryWrapper
import io.infra.structure.activity.admin.domain.model.ComponentDefinition
import io.infra.structure.activity.admin.domain.model.ComponentLinkOperator
import io.infra.structure.activity.admin.domain.model.ComponentNode
import io.infra.structure.activity.admin.domain.model.ComponentNodeType
import io.infra.structure.activity.admin.domain.model.ComponentReferenceMode
import io.infra.structure.activity.admin.dto.ActivityComponentResponse
import io.infra.structure.activity.admin.dto.ActivityFormField
import io.infra.structure.activity.admin.dto.ActivityFormResponse
import io.infra.structure.activity.admin.dto.ActivityResponse
import io.infra.structure.activity.admin.dto.ActivityTemplateResponse
import io.infra.structure.activity.admin.dto.CreateActivityRequest
import io.infra.structure.activity.admin.dto.CreateComponentRequest
import io.infra.structure.activity.admin.dto.CreateTemplateRequest
import io.infra.structure.activity.admin.dto.FrontendActivityResponse
import io.infra.structure.activity.admin.dto.TemplateComponentResponse
import io.infra.structure.activity.admin.dto.TemplateRewardTemplateResponse
import io.infra.structure.activity.admin.dto.UpdateActivityDebugConfigurationRequest
import io.infra.structure.activity.persistence.entity.ActivityComponentEntity
import io.infra.structure.activity.persistence.entity.ActivityEntity
import io.infra.structure.activity.persistence.entity.ActivityTemplateComponentEntity
import io.infra.structure.activity.persistence.entity.ActivityTemplateEntity
import io.infra.structure.activity.persistence.entity.ActivityTemplateRewardTemplateEntity
import io.infra.structure.activity.persistence.mapper.ActivityComponentMapper
import io.infra.structure.activity.persistence.mapper.ActivityMapper
import io.infra.structure.activity.persistence.mapper.ActivityTemplateComponentMapper
import io.infra.structure.activity.persistence.mapper.ActivityTemplateMapper
import io.infra.structure.activity.persistence.mapper.ActivityTemplateRewardTemplateMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 活动配置的领域服务。
 *
 * 负责把可复用组件编排为模板，并在创建活动时依据模板展开字段、校验填写值和保存 JSON 配置。
 */
@Service
class ActivityConfigurationService(
    private val componentMapper: ActivityComponentMapper,
    private val templateMapper: ActivityTemplateMapper,
    private val templateComponentMapper: ActivityTemplateComponentMapper,
    private val templateRewardTemplateMapper: ActivityTemplateRewardTemplateMapper,
    private val activityMapper: ActivityMapper,
    private val rewardConfigurationService: RewardConfigurationService,
    private val activityTaskService: ActivityTaskService,
    private val objectMapper: ObjectMapper
) {

    /** 查询所有活动组件，按创建顺序返回。 */
    fun listComponents(): List<ActivityComponentResponse> = componentMapper
        .selectListByQuery(QueryWrapper.create().orderBy("id"))
        .orEmpty()
        .map(::componentResponse)

    /** 新建一个含递归输入节点的可复用组件。 */
    @Transactional
    fun createComponent(request: CreateComponentRequest): ActivityComponentResponse {
        validateCode(request.code, "组件编码")
        require(request.name.isNotBlank()) { "组件名称不能为空" }
        validateDefinition(request.definition)
        validateComponentReferences(null, request.definition)
        require(componentMapper.selectOneByQuery(QueryWrapper.create().eq("code", request.code)) == null) {
            "组件编码已存在"
        }

        val now = System.currentTimeMillis()
        val entity = ActivityComponentEntity(
            code = request.code.trim(),
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf(String::isNotBlank),
            definitionJson = objectMapper.writeValueAsString(request.definition),
            enabled = request.enabled,
            createTime = now,
            updateTime = now
        )
        componentMapper.insert(entity)
        return componentResponse(entity)
    }

    /** 更新已保存组件的展示信息、可用状态和递归字段定义。 */
    @Transactional
    fun updateComponent(componentId: Long, request: CreateComponentRequest): ActivityComponentResponse {
        val entity = requiredComponent(componentId)
        require(request.code.trim() == entity.code) { "组件编码创建后不能修改" }
        require(request.name.isNotBlank()) { "组件名称不能为空" }
        validateDefinition(request.definition)
        validateComponentReferences(componentId, request.definition)

        entity.name = request.name.trim()
        entity.description = request.description?.trim()?.takeIf(String::isNotBlank)
        entity.definitionJson = objectMapper.writeValueAsString(request.definition)
        entity.enabled = request.enabled
        entity.updateTime = System.currentTimeMillis()
        require(componentMapper.update(entity) == 1) { "组件更新失败" }
        return componentResponse(entity)
    }

    /** 删除未被模板或其他已保存组件引用的组件。 */
    @Transactional
    fun deleteComponent(componentId: Long) {
        val component = requiredComponent(componentId)
        val usedByTemplateBinding = templateComponentMapper
            .selectOneByQuery(QueryWrapper.create().eq("component_id", componentId)) != null
        val usedByTemplateDefinition = templateMapper
            .selectListByQuery(QueryWrapper.create())
            .orEmpty()
            .any { template -> referencedComponentIds(templateDefinition(template).nodes).contains(componentId) }
        val usedByComponent = componentMapper
            .selectListByQuery(QueryWrapper.create())
            .orEmpty()
            .filter { it.id != componentId }
            .any { candidate -> referencesComponent(requireNotNull(candidate.id), componentId, mutableSetOf()) }
        require(!usedByTemplateBinding && !usedByTemplateDefinition && !usedByComponent) {
            "组件 ${component.name} 已被模板或其他组件引用，不能删除"
        }
        require(componentMapper.deleteById(componentId) == 1) { "组件删除失败" }
    }

    /** 查询所有活动模板及其引用组件。 */
    fun listTemplates(): List<ActivityTemplateResponse> = templateMapper
        .selectListByQuery(QueryWrapper.create().orderBy("id"))
        .orEmpty()
        .map(::templateResponse)

    /** 查询一个模板及其可渲染的动态表单。 */
    fun getTemplateForm(templateId: Long): ActivityFormResponse {
        val template = templateResponse(requiredTemplate(templateId))
        return ActivityFormResponse(
            template = template,
            fields = flattenNodes(
                componentCode = "template",
                nodes = template.definition.nodes,
                componentRequired = false
            ) + template.components.map { binding ->
                val children = flattenNodes(
                    // 挂载键由模板维护，确保同一组件多次挂载时字段路径保持独立且稳定。
                    componentCode = binding.mountKey,
                    nodes = binding.component.definition.nodes,
                    componentRequired = binding.required,
                    depth = 1,
                    referencedComponentIds = setOf(binding.component.id)
                )
                ActivityFormField(
                    key = binding.mountKey,
                    label = binding.mountTitle,
                    type = ComponentNodeType.COMPONENT,
                    required = binding.required,
                    placeholder = null,
                    defaultValue = null,
                    options = emptyList(),
                    collection = binding.mountMode == ComponentReferenceMode.ARRAY,
                    children = children,
                    depth = 0
                )
            } + template.rewardTemplates.map { binding ->
                rewardConfigurationService.formField(
                    template = binding.rewardTemplate,
                    mountKey = binding.mountKey,
                    mountTitle = binding.mountTitle,
                    required = binding.required
                )
            }
        )
    }

    /** 新建活动模板并按传入顺序绑定已存在的组件。 */
    @Transactional
    fun createTemplate(request: CreateTemplateRequest): ActivityTemplateResponse {
        validateCode(request.code, "模板编码")
        require(request.name.isNotBlank()) { "模板名称不能为空" }
        val components = validateTemplateConfiguration(request)
        validateTemplateRewardTemplates(request)
        require(templateMapper.selectOneByQuery(QueryWrapper.create().eq("code", request.code)) == null) {
            "模板编码已存在"
        }
        val now = System.currentTimeMillis()
        val template = ActivityTemplateEntity(
            code = request.code.trim(),
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf(String::isNotBlank),
            definitionJson = objectMapper.writeValueAsString(request.definition),
            enabled = request.enabled,
            createTime = now,
            updateTime = now
        )
        templateMapper.insert(template)
        val templateId = requireNotNull(template.id) { "模板保存后未生成主键" }
        request.components.forEachIndexed { index, binding ->
            templateComponentMapper.insert(
                ActivityTemplateComponentEntity(
                    templateId = templateId,
                    componentId = components[index].id ?: error("组件主键不能为空"),
                    mountKey = binding.mountKey.trim(),
                    mountTitle = binding.mountTitle.trim(),
                    mountMode = binding.mountMode.name,
                    sortNo = index + 1,
                    required = binding.required,
                    overridesJson = binding.overrides?.let(objectMapper::writeValueAsString)
                )
            )
        }
        saveTemplateRewardBindings(templateId, request)
        return templateResponse(template)
    }

    /** 更新模板的展示信息、普通输入项及组件挂载编排，模板编码保持不变。 */
    @Transactional
    fun updateTemplate(templateId: Long, request: CreateTemplateRequest): ActivityTemplateResponse {
        val template = requiredTemplate(templateId)
        require(request.code.trim() == template.code) { "模板编码创建后不能修改" }
        require(request.name.isNotBlank()) { "模板名称不能为空" }
        // 已停用的历史组件允许原样保留，避免模板无法修改名称或启用状态。
        val existingComponentIds = templateComponentMapper
            .selectListByQuery(QueryWrapper.create().eq("template_id", templateId))
            .orEmpty()
            .map { it.componentId }
            .toSet()
        val components = validateTemplateConfiguration(request, existingComponentIds)
        val existingRewardTemplateIds = templateRewardTemplateMapper
            .selectListByQuery(QueryWrapper.create().eq("template_id", templateId))
            .orEmpty()
            .map { it.rewardTemplateId }
            .toSet()
        validateTemplateRewardTemplates(request, existingRewardTemplateIds)

        template.name = request.name.trim()
        template.description = request.description?.trim()?.takeIf(String::isNotBlank)
        template.definitionJson = objectMapper.writeValueAsString(request.definition)
        template.enabled = request.enabled
        template.updateTime = System.currentTimeMillis()
        require(templateMapper.update(template) == 1) { "模板更新失败" }

        templateComponentMapper.deleteByQuery(QueryWrapper.create().eq("template_id", templateId))
        templateRewardTemplateMapper.deleteByQuery(QueryWrapper.create().eq("template_id", templateId))
        request.components.forEachIndexed { index, binding ->
            templateComponentMapper.insert(
                ActivityTemplateComponentEntity(
                    templateId = templateId,
                    componentId = components[index].id ?: error("组件主键不能为空"),
                    mountKey = binding.mountKey.trim(),
                    mountTitle = binding.mountTitle.trim(),
                    mountMode = binding.mountMode.name,
                    sortNo = index + 1,
                    required = binding.required,
                    overridesJson = binding.overrides?.let(objectMapper::writeValueAsString)
                )
            )
        }
        saveTemplateRewardBindings(templateId, request)
        return templateResponse(template)
    }

    /** 删除未被活动使用的模板及其组件挂载记录。 */
    @Transactional
    fun deleteTemplate(templateId: Long) {
        val template = requiredTemplate(templateId)
        require(activityMapper.selectOneByQuery(QueryWrapper.create().eq("template_id", templateId)) == null) {
            "模板 ${template.name} 已被活动使用，不能删除"
        }
        templateComponentMapper.deleteByQuery(QueryWrapper.create().eq("template_id", templateId))
        templateRewardTemplateMapper.deleteByQuery(QueryWrapper.create().eq("template_id", templateId))
        require(templateMapper.deleteById(templateId) == 1) { "模板删除失败" }
    }

    /** 查询所有已保存活动。 */
    fun listActivities(): List<ActivityResponse> = activityMapper
        .selectListByQuery(QueryWrapper.create().orderBy("id", false))
        .orEmpty()
        .map(::activityResponse)

    /**
     * 读取可面向前端构建的活动配置。
     *
     * 该方法统一校验模板类型、模板启用状态、活动状态、上下线状态、有效期与调试白名单，
     * 避免前端活动类型实现分别遗漏可见性判断。
     */
    fun getActivityForFrontend(activityId: Long, templateCode: String, userId: Long): FrontendActivityResponse {
        val activity = requiredActivity(activityId)
        val template = requiredTemplate(activity.templateId)
        require(template.code == templateCode) { "活动类型与活动模板不匹配" }
        require(template.enabled) { "活动模板已停用" }
        require(activity.status == "ACTIVE" && activity.onlineStatus == "ONLINE") { "活动当前不可访问" }

        val debugUserIds = objectMapper.readValue(activity.debugUserIdsJson, USER_ID_LIST_TYPE)
        if (activity.debugMode) {
            require(userId in debugUserIds) { "当前用户不在活动调试白名单中" }
        }
        val effectiveTime = if (activity.debugMode) activity.debugForceTime ?: System.currentTimeMillis() else System.currentTimeMillis()
        if (!activity.validForever) {
            val validStartTime = requireNotNull(activity.validStartTime) { "活动开始时间不能为空" }
            val validEndTime = requireNotNull(activity.validEndTime) { "活动结束时间不能为空" }
            require(effectiveTime in validStartTime until validEndTime) { "活动不在有效期内" }
        }
        return frontendActivityResponse(activity, debugUserIds)
    }

    /** 根据模板动态表单校验并保存活动配置。 */
    @Transactional
    open fun createActivity(request: CreateActivityRequest): ActivityResponse {
        require(request.name.isNotBlank()) { "活动名称不能为空" }
        validateActivityStatuses(request.status, request.onlineStatus)
        validateValidity(request)
        val form = getTemplateForm(request.templateId)
        require(form.template.enabled) { "活动模板已停用，不能创建活动" }
        val values = normalizedActivityValues(request.values)
        validateValues(form.fields, flattenActivityValues(values, multiSelectFieldKeys(form.fields)))

        val now = System.currentTimeMillis()
        val entity = ActivityEntity(
            name = request.name.trim(),
            templateId = request.templateId,
            status = request.status,
            onlineStatus = request.onlineStatus,
            validForever = request.validForever,
            validStartTime = request.validStartTime,
            validEndTime = request.validEndTime,
            formDataJson = objectMapper.writeValueAsString(values),
            createTime = now,
            updateTime = now
        )
        activityMapper.insert(entity)
        // 仅“启用且上线”的活动会生成可执行任务，草稿和下线活动不会占用调度资源。
        activityTaskService.refreshActivityTasks(entity)
        return activityResponse(entity)
    }

    /** 更新活动名称、模板、状态和动态表单配置。 */
    @Transactional
    open fun updateActivity(activityId: Long, request: CreateActivityRequest): ActivityResponse {
        val entity = requiredActivity(activityId)
        require(request.name.isNotBlank()) { "活动名称不能为空" }
        validateActivityStatuses(request.status, request.onlineStatus)
        validateValidity(request)
        val form = getTemplateForm(request.templateId)
        require(form.template.enabled || request.templateId == entity.templateId) {
            "活动模板已停用，不能切换到该模板"
        }
        val values = normalizedActivityValues(request.values)
        validateValues(form.fields, flattenActivityValues(values, multiSelectFieldKeys(form.fields)))

        entity.name = request.name.trim()
        entity.templateId = request.templateId
        entity.status = request.status
        entity.onlineStatus = request.onlineStatus
        entity.validForever = request.validForever
        entity.validStartTime = request.validStartTime
        entity.validEndTime = request.validEndTime
        entity.formDataJson = objectMapper.writeValueAsString(values)
        entity.updateTime = System.currentTimeMillis()
        require(activityMapper.update(entity) == 1) { "活动更新失败" }
        // 模板、有效期与上下线状态都可能影响下一次触发时间，更新后重新生成快照。
        activityTaskService.refreshActivityTasks(entity)
        return activityResponse(entity)
    }

    /** 复制活动配置，并将副本固定创建为草稿和下线状态。 */
    @Transactional
    fun copyActivity(activityId: Long): ActivityResponse {
        val source = requiredActivity(activityId)
        val now = System.currentTimeMillis()
        val copied = ActivityEntity(
            name = "${source.name} 副本",
            templateId = source.templateId,
            status = "DRAFT",
            onlineStatus = "OFFLINE",
            validForever = source.validForever,
            validStartTime = source.validStartTime,
            validEndTime = source.validEndTime,
            debugMode = source.debugMode,
            debugUserIdsJson = source.debugUserIdsJson,
            debugForceTime = source.debugForceTime,
            formDataJson = source.formDataJson,
            createTime = now,
            updateTime = now
        )
        activityMapper.insert(copied)
        return activityResponse(copied)
    }

    /** 删除活动及其已经保存的动态表单配置。 */
    @Transactional
    open fun deleteActivity(activityId: Long) {
        requiredActivity(activityId)
        require(activityMapper.deleteById(activityId) == 1) { "活动删除失败" }
    }

    /** 仅切换活动上下线状态，避免列表快捷操作覆盖其他活动配置。 */
    @Transactional
    open fun updateActivityOnlineStatus(activityId: Long, onlineStatus: String): ActivityResponse {
        val entity = requiredActivity(activityId)
        validateActivityStatuses(entity.status, onlineStatus)
        entity.onlineStatus = onlineStatus
        entity.updateTime = System.currentTimeMillis()
        require(activityMapper.update(entity) == 1) { "活动上下线状态更新失败" }
        // 上线时创建或刷新任务；下线时取消尚未执行的任务。
        activityTaskService.refreshActivityTasks(entity)
        return activityResponse(entity)
    }

    /** 仅更新活动调试开关、用户白名单和强制指定时间，不覆盖活动主体配置。 */
    @Transactional
    fun updateActivityDebugConfiguration(
        activityId: Long,
        request: UpdateActivityDebugConfigurationRequest
    ): ActivityResponse {
        val entity = requiredActivity(activityId)
        val debugConfiguration = normalizedDebugConfiguration(
            request.debugMode,
            request.debugUserIds,
            request.debugForceTime
        )
        entity.debugMode = debugConfiguration.enabled
        entity.debugUserIdsJson = objectMapper.writeValueAsString(debugConfiguration.userIds)
        entity.debugForceTime = debugConfiguration.forceTime
        entity.updateTime = System.currentTimeMillis()
        require(activityMapper.update(entity) == 1) { "活动调试配置更新失败" }
        return activityResponse(entity)
    }

    /** 将组件实体转换为公开视图。 */
    private fun componentResponse(entity: ActivityComponentEntity): ActivityComponentResponse = ActivityComponentResponse(
        id = requireNotNull(entity.id) { "组件主键不能为空" },
        code = entity.code,
        name = entity.name,
        description = entity.description,
        definition = objectMapper.readValue(entity.definitionJson, ComponentDefinition::class.java),
        enabled = entity.enabled
    )

    /** 读取模板已关联的组件并组装模板视图。 */
    private fun templateResponse(entity: ActivityTemplateEntity): ActivityTemplateResponse {
        val templateId = requireNotNull(entity.id) { "模板主键不能为空" }
        val bindings = templateComponentMapper
            .selectListByQuery(QueryWrapper.create().eq("template_id", templateId).orderBy("sort_no"))
            .orEmpty()
            .map { binding ->
                TemplateComponentResponse(
                    id = requireNotNull(binding.id) { "模板组件关联主键不能为空" },
                    sortNo = binding.sortNo,
                    mountKey = binding.mountKey,
                    mountTitle = binding.mountTitle,
                    mountMode = ComponentReferenceMode.valueOf(binding.mountMode),
                    required = binding.required,
                    component = componentResponse(requiredComponent(binding.componentId))
                )
            }
        val rewardTemplateBindings = templateRewardTemplateMapper
            .selectListByQuery(QueryWrapper.create().eq("template_id", templateId).orderBy("sort_no"))
            .orEmpty()
            .map { binding ->
                TemplateRewardTemplateResponse(
                    id = requireNotNull(binding.id) { "模板奖励模板关联主键不能为空" },
                    sortNo = binding.sortNo,
                    mountKey = binding.mountKey,
                    mountTitle = binding.mountTitle,
                    required = binding.required,
                    rewardTemplate = rewardConfigurationService.getTemplate(binding.rewardTemplateId)
                )
            }
        return ActivityTemplateResponse(
            id = templateId,
            code = entity.code,
            name = entity.name,
            description = entity.description,
            enabled = entity.enabled,
            definition = templateDefinition(entity),
            components = bindings,
            rewardTemplates = rewardTemplateBindings
        )
    }

    /** 将活动实体转换为公开视图。 */
    private fun activityResponse(entity: ActivityEntity): ActivityResponse = ActivityResponse(
        id = requireNotNull(entity.id) { "活动主键不能为空" },
        name = entity.name,
        templateId = entity.templateId,
        status = entity.status,
        onlineStatus = entity.onlineStatus,
        validForever = entity.validForever,
        validStartTime = entity.validStartTime,
        validEndTime = entity.validEndTime,
        debugMode = entity.debugMode,
        debugUserIds = objectMapper.readValue(entity.debugUserIdsJson, USER_ID_LIST_TYPE),
        debugForceTime = entity.debugForceTime,
        values = objectMapper.readValue(entity.formDataJson, MAP_TYPE)
    )

    /** 将通过前端可见性校验的活动实体转换为内部传输模型。 */
    private fun frontendActivityResponse(entity: ActivityEntity, debugUserIds: List<Long>): FrontendActivityResponse = FrontendActivityResponse(
        id = requireNotNull(entity.id) { "活动主键不能为空" },
        name = entity.name,
        templateId = entity.templateId,
        status = entity.status,
        onlineStatus = entity.onlineStatus,
        validForever = entity.validForever,
        validStartTime = entity.validStartTime,
        validEndTime = entity.validEndTime,
        debugMode = entity.debugMode,
        debugUserIds = debugUserIds,
        debugForceTime = entity.debugForceTime,
        formDataJson = entity.formDataJson,
        createTime = requireNotNull(entity.createTime) { "活动创建时间不能为空" },
        updateTime = requireNotNull(entity.updateTime) { "活动更新时间不能为空" }
    )

    /** 递归展平组件节点，使前端可按统一表单控件进行渲染。 */
    private fun flattenNodes(
        componentCode: String,
        nodes: List<ComponentNode>,
        componentRequired: Boolean,
        parentPath: String = componentCode,
        depth: Int = 0,
        referencedComponentIds: Set<Long> = emptySet(),
        linkRootPath: String = componentCode
    ): List<ActivityFormField> = nodes.map { node ->
        val key = "$parentPath.${node.key}"
        val effectiveRequired = componentRequired || node.required
        val children = if (node.type == ComponentNodeType.COMPONENT) {
            val referencedComponent = requiredComponent(requireNotNull(node.componentId) { "子组件不能为空" })
            val referencedComponentId = requireNotNull(referencedComponent.id) { "子组件主键不能为空" }
            require(referencedComponentId !in referencedComponentIds) { "组件引用存在循环，无法生成活动表单" }
            flattenNodes(
                componentCode = componentCode,
                nodes = componentDefinition(referencedComponent).nodes,
                componentRequired = effectiveRequired,
                parentPath = key,
                depth = depth + 1,
                referencedComponentIds = referencedComponentIds + referencedComponentId,
                linkRootPath = key
            )
        } else {
            flattenNodes(
                componentCode = componentCode,
                nodes = node.children,
                componentRequired = effectiveRequired,
                parentPath = key,
                depth = depth + 1,
                referencedComponentIds = referencedComponentIds,
                linkRootPath = linkRootPath
            )
        }
        ActivityFormField(
            key = key,
            label = node.label,
            type = node.type,
            // 分组只承担层级展示职责，必填规则由其中的实际输入字段承担。
            required = node.type != ComponentNodeType.GROUP && effectiveRequired,
            uniqueInArray = node.uniqueInArray,
            linkRules = node.linkRules.map { rule -> rule.copy(targetKey = "$linkRootPath.${rule.targetKey}") },
            placeholder = node.placeholder,
            defaultValue = node.defaultValue,
            options = node.options,
            collection = node.type == ComponentNodeType.COMPONENT && node.componentMode == ComponentReferenceMode.ARRAY,
            children = children,
            depth = depth
        )
    }

    /** 校验组件定义的递归结构、键名和下拉候选项。 */
    private fun validateDefinition(definition: ComponentDefinition) {
        require(definition.nodes.isNotEmpty()) { "组件至少需要配置一个字段或子组件" }
        validateNodes(definition.nodes, "")
        validateLinkRules(definition.nodes)
    }

    /** 校验模板可选的普通输入字段定义。 */
    private fun validateTemplateDefinition(definition: ComponentDefinition) {
        if (definition.nodes.isNotEmpty()) {
            validateNodes(definition.nodes, "")
            validateLinkRules(definition.nodes)
        }
    }

    /** 校验模板定义和组件挂载，并返回按请求顺序读取到的组件实体。 */
    private fun validateTemplateConfiguration(
        request: CreateTemplateRequest,
        allowedDisabledComponentIds: Set<Long> = emptySet()
    ): List<ActivityComponentEntity> {
        require(request.components.isNotEmpty() || request.rewardTemplates.isNotEmpty() || request.definition.nodes.isNotEmpty()) {
            "活动模板至少需要配置一个组件、奖励模板或普通输入项"
        }
        validateTemplateDefinition(request.definition)
        validateComponentReferences(null, request.definition)
        request.components.forEach { binding -> validateCode(binding.mountKey, "挂载键") }
        require(request.components.all { it.mountTitle.isNotBlank() && it.mountTitle.trim().length <= 128 }) {
            "挂载标题不能为空且不能超过 128 个字符"
        }
        require(request.components.map { it.mountKey }.toSet().size == request.components.size) {
            "同一模板中的挂载键不能重复"
        }
        return request.components.map { binding ->
            requiredComponent(binding.componentId).also { component ->
                require(component.enabled || binding.componentId in allowedDisabledComponentIds) {
                    "组件 ${component.code} 已停用，不能用于活动模板"
                }
            }
        }
    }

    /** 校验活动模板对奖励模板的挂载配置。 */
    private fun validateTemplateRewardTemplates(
        request: CreateTemplateRequest,
        allowedDisabledTemplateIds: Set<Long> = emptySet()
    ) {
        request.rewardTemplates.forEach { binding -> validateCode(binding.mountKey, "奖励挂载键") }
        require(request.rewardTemplates.all { it.mountTitle.isNotBlank() && it.mountTitle.trim().length <= 128 }) {
            "奖励挂载标题不能为空且不能超过 128 个字符"
        }
        val rootKeys = request.definition.nodes.map { it.key } +
            request.components.map { it.mountKey } + request.rewardTemplates.map { it.mountKey }
        require(rootKeys.toSet().size == rootKeys.size) { "同一活动模板中的字段键和挂载键不能重复" }
        request.rewardTemplates.forEach { binding ->
            rewardConfigurationService.selectableTemplate(
                templateId = binding.rewardTemplateId,
                allowDisabled = binding.rewardTemplateId in allowedDisabledTemplateIds
            )
        }
    }

    /** 保存活动模板与奖励模板的挂载记录。 */
    private fun saveTemplateRewardBindings(templateId: Long, request: CreateTemplateRequest) {
        request.rewardTemplates.forEachIndexed { index, binding ->
            templateRewardTemplateMapper.insert(
                ActivityTemplateRewardTemplateEntity(
                    templateId = templateId,
                    rewardTemplateId = binding.rewardTemplateId,
                    mountKey = binding.mountKey.trim(),
                    mountTitle = binding.mountTitle.trim(),
                    sortNo = index + 1,
                    required = binding.required
                )
            )
        }
    }

    /** 校验同级节点，并递归校验其子节点。 */
    private fun validateNodes(nodes: List<ComponentNode>, parentPath: String) {
        require(nodes.map { it.key }.toSet().size == nodes.size) { "同一组件层级中字段键不能重复" }
        nodes.forEach { node ->
            validateCode(node.key, "字段键")
            require(node.label.isNotBlank()) { "字段标题不能为空" }
            require(node.type != ComponentNodeType.PRIZE) { "固定奖品组件只能在奖励模板中配置" }
            if (node.type == ComponentNodeType.SELECT || node.type == ComponentNodeType.MULTI_SELECT) {
                require(node.options.isNotEmpty()) { "下拉字段 ${node.key} 至少需要一个候选项" }
                require(node.options.all { it.value.isNotBlank() && it.label.isNotBlank() }) { "下拉候选项的值和名称不能为空" }
                require(node.options.map { it.value }.toSet().size == node.options.size) { "下拉候选项的值不能重复" }
                val defaultValues = if (node.type == ComponentNodeType.MULTI_SELECT) {
                    node.defaultValue?.split(',')?.filter(String::isNotBlank).orEmpty()
                } else {
                    listOfNotNull(node.defaultValue)
                }
                require(defaultValues.all { defaultValue -> node.options.any { it.value == defaultValue } }) {
                    "下拉字段 ${node.key} 的默认值必须是候选项之一"
                }
            }
            if (node.type == ComponentNodeType.COMPONENT) {
                require(node.componentId != null) { "子组件字段 ${node.key} 必须选择一个组件" }
                require(node.options.isEmpty()) { "子组件字段 ${node.key} 不能配置下拉候选项" }
                require(node.defaultValue.isNullOrBlank()) { "子组件字段 ${node.key} 不能配置默认值" }
                require(node.children.isEmpty()) { "子组件字段 ${node.key} 不能继续配置字段节点" }
            } else {
                require(node.componentId == null) { "仅子组件类型可以配置引用组件" }
                require(node.componentMode == ComponentReferenceMode.SINGLE) { "仅子组件类型可以配置数组形式" }
            }
            if (node.type == ComponentNodeType.NUMBER && !node.defaultValue.isNullOrBlank()) {
                require(node.defaultValue.toBigDecimalOrNull() != null) { "数字字段 ${node.key} 的默认值必须是数字" }
            }
            if (node.type == ComponentNodeType.DATE && !node.defaultValue.isNullOrBlank()) {
                require(runCatching { LocalDate.parse(node.defaultValue) }.isSuccess) { "日期字段 ${node.key} 的默认值必须是 yyyy-MM-dd" }
            }
            if (node.type == ComponentNodeType.DATE_TIME && !node.defaultValue.isNullOrBlank()) {
                require(runCatching { LocalDateTime.parse(node.defaultValue) }.isSuccess) {
                    "日期时间字段 ${node.key} 的默认值必须是 yyyy-MM-ddTHH:mm"
                }
            }
            if (node.type == ComponentNodeType.GROUP) {
                require(node.children.isNotEmpty()) { "分组字段 ${node.key} 至少需要一个子字段" }
                require(node.defaultValue.isNullOrBlank()) { "分组字段 ${node.key} 不能配置默认值" }
            }
            validateNodes(node.children, "$parentPath.${node.key}")
        }
    }

    /** 联动字段只能指向同一份组件定义中的可比较输入项。 */
    private fun validateLinkRules(nodes: List<ComponentNode>) {
        val fields = linkedFieldPaths(nodes)
        fields.forEach { (sourceKey, source) ->
            require(source.linkRules.map { it.targetKey }.toSet().size == source.linkRules.size) {
                "字段 $sourceKey 的联动目标不能重复"
            }
            if (source.linkRules.isEmpty()) return@forEach
            require(source.type !in setOf(ComponentNodeType.GROUP, ComponentNodeType.COMPONENT, ComponentNodeType.PRIZE, ComponentNodeType.MULTI_SELECT)) {
                "字段 $sourceKey 不能配置联动规则"
            }
            source.linkRules.forEach { rule ->
                val target = fields[rule.targetKey]
                require(target != null && rule.targetKey != sourceKey) { "字段 $sourceKey 的联动目标无效" }
                require(target.type == source.type) { "字段 $sourceKey 的联动字段类型必须一致" }
                if (rule.operator !in setOf(ComponentLinkOperator.EQUAL, ComponentLinkOperator.NOT_EQUAL)) {
                    require(source.type in setOf(ComponentNodeType.NUMBER, ComponentNodeType.DATE, ComponentNodeType.DATE_TIME)) {
                        "字段 $sourceKey 仅数字、日期和日期时间支持大小比较"
                    }
                }
            }
        }
    }

    private fun linkedFieldPaths(nodes: List<ComponentNode>): Map<String, ComponentNode> {
        val fields = linkedMapOf<String, ComponentNode>()
        fun visit(children: List<ComponentNode>, parentPath: String) {
            children.forEach { node ->
                val path = if (parentPath.isBlank()) node.key else "$parentPath.${node.key}"
                fields[path] = node
                visit(node.children, path)
            }
        }
        visit(nodes, "")
        return fields
    }

    /** 校验子组件存在，且更新后不会形成组件引用闭环。 */
    private fun validateComponentReferences(componentId: Long?, definition: ComponentDefinition) {
        referencedComponentIds(definition.nodes).forEach { referencedId ->
            requiredComponent(referencedId)
            if (componentId != null) {
                require(referencedId != componentId && !referencesComponent(referencedId, componentId, mutableSetOf())) {
                    "子组件引用不能形成循环"
                }
            }
        }
    }

    /** 递归读取定义中所有子组件引用。 */
    private fun referencedComponentIds(nodes: List<ComponentNode>): Set<Long> = nodes
        .flatMap { node -> listOfNotNull(node.componentId) + referencedComponentIds(node.children) }
        .toSet()

    /** 判断一个已保存组件是否直接或间接引用目标组件。 */
    private fun referencesComponent(componentId: Long, targetComponentId: Long, visited: MutableSet<Long>): Boolean {
        if (componentId == targetComponentId) {
            return true
        }
        if (!visited.add(componentId)) {
            return false
        }
        return referencedComponentIds(componentDefinition(requiredComponent(componentId)).nodes)
            .any { referencedId -> referencesComponent(referencedId, targetComponentId, visited) }
    }

    /** 校验活动填写值与模板动态字段一致。 */
    private fun validateValues(fields: List<ActivityFormField>, values: Map<String, Any?>) {
        val acceptedKeys = mutableSetOf<String>()
        fields.forEach { field -> validateFieldValue(field, field.key, values, acceptedKeys) }
        val unknownKeys = values.keys - acceptedKeys
        require(unknownKeys.isEmpty()) { "存在不属于该模板的配置字段：${unknownKeys.joinToString()}" }
    }

    /** 按组件层级校验单个字段、子组件和子组件数组的填写值。 */
    private fun validateFieldValue(
        field: ActivityFormField,
        key: String,
        values: Map<String, Any?>,
        acceptedKeys: MutableSet<String>
    ) {
        if (field.type == ComponentNodeType.PRIZE) {
            validatePrizeFieldValue(field, key, values, acceptedKeys)
            return
        }
        if (field.type == ComponentNodeType.GROUP) {
            field.children.forEach { child -> validateFieldValue(child, nestedFieldKey(child, field.key, key), values, acceptedKeys) }
            return
        }
        if (field.type == ComponentNodeType.COMPONENT) {
            if (!field.collection) {
                field.children.forEach { child -> validateFieldValue(child, nestedFieldKey(child, field.key, key), values, acceptedKeys) }
                return
            }
            val indexes = values.keys.asSequence()
                .filter { it.startsWith("$key.") }
                .mapNotNull { it.removePrefix("$key.").substringBefore('.').toIntOrNull() }
                .toSet()
            if (field.required) {
                require(indexes.isNotEmpty()) { "${field.label} 至少需要配置一个子组件" }
            }
            validateUniqueArrayFields(field.children, field.key, key, indexes, values)
            indexes.forEach { index ->
                field.children.forEach { child ->
                    validateFieldValue(child, nestedFieldKey(child, field.key, "$key.$index"), values, acceptedKeys)
                }
            }
            return
        }

        acceptedKeys += key
        val value = values[key]
        validateLinkedValues(field, key, values)
        if (field.type == ComponentNodeType.MULTI_SELECT) {
            val selectedValues = when (value) {
                null -> emptyList()
                is Iterable<*> -> value.map {
                    requireNotNull(it) { "${field.label} 的候选值不能为空" }.toString()
                }
                else -> throw IllegalArgumentException("${field.label} 必须使用数组保存多选值")
            }
            if (field.required) {
                require(selectedValues.isNotEmpty()) { "${field.label} 为必填项" }
            }
            require(selectedValues.all { selectedValue -> field.options.any { it.value == selectedValue } }) {
                "${field.label} 的候选值无效"
            }
            return
        }
        if (field.required) {
            require(!isBlank(value)) { "${field.label} 为必填项" }
        }
        if (field.type == ComponentNodeType.SELECT && !isBlank(value)) {
            require(field.options.any { it.value == value.toString() }) { "${field.label} 的候选值无效" }
        }
    }

    /** 校验当前字段与其联动目标的比较关系；空值交由各字段自身的必填规则处理。 */
    private fun validateLinkedValues(field: ActivityFormField, key: String, values: Map<String, Any?>) {
        field.linkRules.forEach { rule ->
            val targetKey = resolveLinkedKey(field.key, key, rule.targetKey)
            val left = values[key]?.toString()?.trim().orEmpty()
            val right = values[targetKey]?.toString()?.trim().orEmpty()
            if (left.isBlank() || right.isBlank()) return@forEach
            val comparison = when (field.type) {
                ComponentNodeType.NUMBER -> left.toBigDecimalOrNull()?.let { leftNumber ->
                    right.toBigDecimalOrNull()?.let { rightNumber -> leftNumber.compareTo(rightNumber) }
                }
                ComponentNodeType.DATE -> runCatching { LocalDate.parse(left).compareTo(LocalDate.parse(right)) }.getOrNull()
                ComponentNodeType.DATE_TIME -> runCatching { LocalDateTime.parse(left).compareTo(LocalDateTime.parse(right)) }.getOrNull()
                else -> left.compareTo(right)
            }
            require(comparison != null && when (rule.operator) {
                ComponentLinkOperator.GREATER_THAN -> comparison > 0
                ComponentLinkOperator.LESS_THAN -> comparison < 0
                ComponentLinkOperator.EQUAL -> comparison == 0
                ComponentLinkOperator.GREATER_OR_EQUAL -> comparison >= 0
                ComponentLinkOperator.LESS_OR_EQUAL -> comparison <= 0
                ComponentLinkOperator.NOT_EQUAL -> comparison != 0
            }) {
                "${field.label} 不符合联动规则"
            }
        }
    }

    /** 将数组实例中的字段路径映射到同一实例下的联动目标路径。 */
    private fun resolveLinkedKey(schemaFieldKey: String, actualFieldKey: String, schemaTargetKey: String): String {
        val schemaParts = schemaFieldKey.split('.')
        val actualParts = actualFieldKey.split('.')
        val inserted = mutableMapOf<Int, MutableList<String>>()
        var matched = 0
        actualParts.forEach { part ->
            if (matched < schemaParts.size && part == schemaParts[matched]) matched++
            else if (part.toIntOrNull() != null) inserted.getOrPut(matched) { mutableListOf() }.add(part)
        }
        return schemaTargetKey.split('.').flatMapIndexed { index, part ->
            (inserted[index].orEmpty()) + part
        }.let { parts -> (parts + inserted[schemaTargetKey.split('.').size].orEmpty()).joinToString(".") }
    }

    /** 校验当前数组实例中声明为“数组内唯一”的字段，嵌套数组交给其自身的校验层处理。 */
    private fun validateUniqueArrayFields(
        fields: List<ActivityFormField>,
        arrayFieldKey: String,
        arrayKey: String,
        indexes: Set<Int>,
        values: Map<String, Any?>
    ) {
        fun visit(children: List<ActivityFormField>, schemaParentKey: String, parentKeys: List<String>) {
            children.forEach { field ->
                val resolvedKeys = parentKeys.map { parentKey -> nestedFieldKey(field, schemaParentKey, parentKey) }
                if (field.uniqueInArray && field.type !in setOf(ComponentNodeType.GROUP, ComponentNodeType.COMPONENT, ComponentNodeType.PRIZE)) {
                    val configuredValues = resolvedKeys.mapNotNull { resolvedKey ->
                        values[resolvedKey]?.takeUnless(::isBlank)?.toString()
                    }
                    require(configuredValues.size == configuredValues.toSet().size) {
                        "${field.label} 在 ${arrayKey.substringAfterLast('.')} 数组中不能重复"
                    }
                }
                if (field.type == ComponentNodeType.GROUP || (field.type == ComponentNodeType.COMPONENT && !field.collection)) {
                    visit(field.children, field.key, resolvedKeys)
                }
            }
        }
        visit(fields, arrayFieldKey, indexes.map { index -> "$arrayKey.$index" })
    }

    /** 校验奖品组件的固定字段和扩展字段；装扮和礼物必须提供奖品 ID。 */
    private fun validatePrizeFieldValue(
        field: ActivityFormField,
        key: String,
        values: Map<String, Any?>,
        acceptedKeys: MutableSet<String>
    ) {
        if (field.collection) {
            val indexes = values.keys.asSequence()
                .filter { it.startsWith("$key.") }
                .mapNotNull { it.removePrefix("$key.").substringBefore('.').toIntOrNull() }
                .toSet()
            if (field.collectionSize != null) {
                val expectedIndexes = (0 until field.collectionSize).toSet()
                require(indexes == expectedIndexes) { "${field.label} 必须配置 ${field.collectionSize} 个奖品" }
            } else if (field.required) {
                require(indexes.isNotEmpty()) { "${field.label} 至少需要配置一个奖品" }
            }
            validateUniqueArrayFields(field.children, field.key, key, indexes, values)
            indexes.forEach { index ->
                validatePrizeFieldValue(
                    field.copy(collection = false, required = field.required || field.collectionSize != null),
                    "$key.$index",
                    values,
                    acceptedKeys
                )
            }
            return
        }
        val keys = PRIZE_PROPERTY_KEYS.associateWith { property -> "$key.$property" }
        acceptedKeys += keys.values
        val prizeType = values[keys.getValue("prize_type")]?.toString()?.trim().orEmpty()
        val hasValue = keys.values.any { candidate -> !isBlank(values[candidate]) } || field.children.any { child ->
            val childKey = nestedFieldKey(child, field.key, key)
            values.keys.any { candidate -> candidate == childKey || candidate.startsWith("$childKey.") }
        }
        if (!hasValue && !field.required) {
            return
        }
        require(prizeType in PRIZE_TYPES) { "${field.label} 的奖品类型不合法" }
        require(!isBlank(values[keys.getValue("prize_name")])) { "${field.label} 的奖品名称不能为空" }
        require(!isBlank(values[keys.getValue("prize_icon")])) { "${field.label} 的奖品图标不能为空" }
        val value = values[keys.getValue("prize_value")]?.toString()?.trim().orEmpty()
        require(value.toBigDecimalOrNull()?.let { it >= java.math.BigDecimal.ZERO } == true) {
            "${field.label} 的奖品价值必须是非负数字"
        }
        val quantity = values[keys.getValue("prize_quantity")]?.toString()?.trim().orEmpty()
        require(quantity.toLongOrNull()?.let { it > 0 } == true) { "${field.label} 的奖品数量必须是正整数" }
        if (prizeType in PRIZE_TYPES_REQUIRING_ID) {
            require(!isBlank(values[keys.getValue("prize_id")])) { "${field.label} 的装扮或礼物奖品 ID 不能为空" }
        }
        field.children.forEach { child ->
            validateFieldValue(child, nestedFieldKey(child, field.key, key), values, acceptedKeys)
        }
    }

    /** 将原始子字段路径替换为当前组件实例的实际路径。 */
    private fun nestedFieldKey(field: ActivityFormField, parentKey: String, resolvedParentKey: String): String =
        resolvedParentKey + field.key.removePrefix(parentKey)

    /** 判断动态表单值是否为空。 */
    private fun isBlank(value: Any?): Boolean = value == null || (value is String && value.isBlank())

    /** 校验活动状态与上下线状态的组合，草稿活动只能保持下线。 */
    private fun validateActivityStatuses(status: String, onlineStatus: String) {
        require(status in ACTIVITY_STATUSES) { "活动状态只支持 DRAFT 或 ACTIVE" }
        require(onlineStatus in ONLINE_STATUSES) { "上下线状态只支持 ONLINE 或 OFFLINE" }
        require(status != "DRAFT" || onlineStatus == "OFFLINE") { "草稿状态的活动不能上线" }
    }

    /** 校验永久有效开关与开始、结束时间的组合符合活动有效期规则。 */
    private fun validateValidity(request: CreateActivityRequest) {
        if (request.validForever) {
            require(request.validStartTime == null && request.validEndTime == null) {
                "永久有效的活动不能设置开始时间或结束时间"
            }
            return
        }
        val validStartTime = requireNotNull(request.validStartTime) { "非永久有效的活动必须设置开始时间" }
        val validEndTime = requireNotNull(request.validEndTime) { "非永久有效的活动必须设置结束时间" }
        require(validEndTime > validStartTime) { "结束时间必须晚于开始时间" }
    }

    /** 统一校验并规范化调试配置，未启用时不保留白名单和强制时间。 */
    private fun normalizedDebugConfiguration(
        enabled: Boolean,
        requestedUserIds: List<Long>,
        requestedForceTime: Long?
    ): DebugConfiguration {
        if (!enabled) {
            return DebugConfiguration(false, emptyList(), null)
        }
        val userIds = requestedUserIds.distinct()
        require(userIds.isNotEmpty()) { "启用调试模式时必须填写用户 ID 白名单" }
        require(userIds.all { it > 0 }) { "用户 ID 白名单只能包含正整数" }
        require(requestedForceTime == null || requestedForceTime >= 0) { "强制指定时间不能早于时间戳起点" }
        return DebugConfiguration(true, userIds, requestedForceTime)
    }

    /** 将页面提交的点号路径或层级对象统一为层级结构，用于持久化活动 JSON。 */
    private fun normalizedActivityValues(values: Map<String, Any?>): Map<String, Any?> {
        val normalized = if (values.keys.any { '.' in it }) nestedActivityValues(values) else values
        return compactActivityValues(normalized)
    }

    /** 将层级对象和数组展开为点号路径，仅用于复用现有动态字段校验。 */
    private fun flattenActivityValues(
        values: Map<String, Any?>,
        multiSelectKeys: Set<String>
    ): Map<String, Any?> {
        val flattened = linkedMapOf<String, Any?>()
        fun append(path: String, value: Any?) {
            when (value) {
                is Map<*, *> -> value.forEach { (key, child) ->
                    val childKey = key as? String ?: throw IllegalArgumentException("活动配置字段键必须是字符串")
                    append("$path.$childKey", child)
                }
                is Iterable<*> -> if (normalizedArrayPath(path) in multiSelectKeys) {
                    flattened[path] = value.toList()
                } else {
                    value.forEachIndexed { index, child ->
                        if (child != null) {
                            append("$path.$index", child)
                        }
                    }
                }
                else -> flattened[path] = value
            }
        }
        values.forEach { (key, value) -> append(key, value) }
        return flattened
    }

    /** 从动态字段树读取所有多选字段的无数组索引路径。 */
    private fun multiSelectFieldKeys(fields: List<ActivityFormField>): Set<String> {
        val keys = mutableSetOf<String>()
        fields.forEach { field ->
            if (field.type == ComponentNodeType.MULTI_SELECT) {
                keys += field.key
            }
            keys += multiSelectFieldKeys(field.children)
        }
        return keys
    }

    /** 去除组件数组生成的数值索引，使数组中的多选字段可匹配其定义路径。 */
    private fun normalizedArrayPath(path: String): String = path.split('.')
        .filterNot { it.toIntOrNull() != null }
        .joinToString(".")

    /** 将旧版点号路径转换为对象和数组，数组索引会在写入前压缩为连续顺序。 */
    private fun nestedActivityValues(values: Map<String, Any?>): Map<String, Any?> {
        val nested = linkedMapOf<String, Any?>()
        values.forEach { (path, value) ->
            require(path.isNotBlank()) { "活动配置字段键不能为空" }
            putNestedValue(nested, path.split('.'), value)
        }
        return nested
    }

    /** 将单个点号路径写入目标对象，并根据下一级路径自动创建对象或数组。 */
    private fun putNestedValue(target: MutableMap<String, Any?>, path: List<String>, value: Any?) {
        val key = path.first()
        if (path.size == 1) {
            target[key] = value
            return
        }
        if (path[1].toIntOrNull() != null) {
            val list = target[key] as? MutableList<Any?> ?: mutableListOf<Any?>().also { target[key] = it }
            putNestedListValue(list, path.drop(1), value)
            return
        }
        val map = target[key] as? MutableMap<String, Any?> ?: linkedMapOf<String, Any?>().also { target[key] = it }
        putNestedValue(map, path.drop(1), value)
    }

    /** 将包含数组索引的剩余路径写入列表对应的对象或嵌套列表。 */
    private fun putNestedListValue(target: MutableList<Any?>, path: List<String>, value: Any?) {
        val index = path.first().toIntOrNull() ?: throw IllegalArgumentException("活动配置数组索引不合法")
        require(index >= 0) { "活动配置数组索引不能小于 0" }
        while (target.size <= index) {
            target.add(null)
        }
        if (path.size == 1) {
            target[index] = value
            return
        }
        val nextIsList = path[1].toIntOrNull() != null
        if (nextIsList) {
            val list = target[index] as? MutableList<Any?> ?: mutableListOf<Any?>().also { target[index] = it }
            putNestedListValue(list, path.drop(1), value)
            return
        }
        val map = target[index] as? MutableMap<String, Any?> ?: linkedMapOf<String, Any?>().also { target[index] = it }
        putNestedValue(map, path.drop(1), value)
    }

    /** 递归删除数组中的空洞，避免删除组件数组项后保存出无意义的 null 元素。 */
    private fun compactActivityValues(values: Map<String, Any?>): Map<String, Any?> = values.mapValuesTo(linkedMapOf<String, Any?>()) { (_, value) ->
        compactActivityValue(value)
    }

    /** 递归压缩活动配置中的数组并保留对象层级。 */
    private fun compactActivityValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associateTo(linkedMapOf<String, Any?>()) { (key, child) ->
            val childKey = key as? String ?: throw IllegalArgumentException("活动配置字段键必须是字符串")
            childKey to compactActivityValue(child)
        }
        is Iterable<*> -> value.filterNotNull().map(::compactActivityValue)
        else -> value
    }

    /** 校验编码或字段键采用小写字母、数字和下划线。 */
    private fun validateCode(value: String, fieldName: String) {
        require(CODE_PATTERN.matches(value)) { "$fieldName 只能包含小写字母、数字和下划线" }
    }

    /** 查询不存在时抛出带业务语义的异常。 */
    private fun requiredComponent(id: Long): ActivityComponentEntity = componentMapper.selectOneById(id)
        ?: throw IllegalArgumentException("活动组件不存在：$id")

    /** 从组件实体的 JSON 定义中恢复递归字段。 */
    private fun componentDefinition(entity: ActivityComponentEntity): ComponentDefinition =
        objectMapper.readValue(entity.definitionJson, ComponentDefinition::class.java)

    /** 读取模板直接挂载的普通输入字段，兼容历史模板尚未配置该字段的记录。 */
    private fun templateDefinition(entity: ActivityTemplateEntity): ComponentDefinition = entity.definitionJson
        ?.let { objectMapper.readValue(it, ComponentDefinition::class.java) }
        ?: ComponentDefinition()

    /** 查询不存在时抛出带业务语义的异常。 */
    private fun requiredTemplate(id: Long): ActivityTemplateEntity = templateMapper.selectOneById(id)
        ?: throw IllegalArgumentException("活动模板不存在：$id")

    /** 查询不存在时抛出带业务语义的异常。 */
    private fun requiredActivity(id: Long): ActivityEntity = activityMapper.selectOneById(id)
        ?: throw IllegalArgumentException("活动不存在：$id")

    /** 活动调试配置的规范化持久化值。 */
    private data class DebugConfiguration(
        /** 是否启用调试模式。 */
        val enabled: Boolean,
        /** 允许调试访问的用户主键。 */
        val userIds: List<Long>,
        /** 调试模式下强制使用的时间戳。 */
        val forceTime: Long?
    )

    private companion object {
        /** 编码和字段键的允许格式。 */
        val CODE_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")

        /** 活动可保存的状态集合。 */
        val ACTIVITY_STATUSES = setOf("DRAFT", "ACTIVE")

        /** 活动可设置的上下线状态集合。 */
        val ONLINE_STATUSES = setOf("ONLINE", "OFFLINE")

        /** 固定奖品组件支持的奖品类型。 */
        val PRIZE_TYPES = setOf("DECORATION", "GIFT", "COIN", "POINT", "COUPON", "OTHER")

        /** 装扮、礼物类型必须提供外部奖品唯一标识。 */
        val PRIZE_TYPES_REQUIRING_ID = setOf("DECORATION", "GIFT")

        /** 固定奖品组件在活动 JSON 中使用的属性键。 */
        val PRIZE_PROPERTY_KEYS = listOf("prize_type", "prize_id", "prize_name", "prize_icon", "prize_value", "prize_display_value", "prize_quantity")

        /** JSON 反序列化活动配置值时使用的泛型类型。 */
        val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}

        /** JSON 反序列化调试用户白名单时使用的泛型类型。 */
        val USER_ID_LIST_TYPE = object : TypeReference<List<Long>>() {}
    }
}
