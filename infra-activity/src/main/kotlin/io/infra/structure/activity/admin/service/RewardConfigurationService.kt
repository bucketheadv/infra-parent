package io.infra.structure.activity.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.mybatisflex.core.query.QueryWrapper
import io.infra.structure.activity.admin.domain.model.ComponentDefinition
import io.infra.structure.activity.admin.domain.model.ComponentLinkOperator
import io.infra.structure.activity.admin.domain.model.ComponentNode
import io.infra.structure.activity.admin.domain.model.ComponentNodeType
import io.infra.structure.activity.admin.domain.model.ComponentOption
import io.infra.structure.activity.admin.domain.model.ComponentReferenceMode
import io.infra.structure.activity.admin.dto.ActivityFormField
import io.infra.structure.activity.admin.dto.CreateRewardComponentRequest
import io.infra.structure.activity.admin.dto.CreateRewardTemplateRequest
import io.infra.structure.activity.admin.dto.RewardComponentPrizeRequest
import io.infra.structure.activity.admin.dto.RewardComponentResponse
import io.infra.structure.activity.admin.dto.PrizeComponentResponse
import io.infra.structure.activity.admin.dto.RewardComponentPrizeResponse
import io.infra.structure.activity.admin.dto.RewardTemplateComponentResponse
import io.infra.structure.activity.admin.dto.RewardTemplateResponse
import io.infra.structure.activity.persistence.entity.RewardComponentEntity
import io.infra.structure.activity.persistence.entity.RewardComponentPrizeEntity
import io.infra.structure.activity.persistence.entity.RewardTemplateComponentEntity
import io.infra.structure.activity.persistence.entity.RewardTemplateEntity
import io.infra.structure.activity.persistence.mapper.ActivityTemplateRewardTemplateMapper
import io.infra.structure.activity.persistence.mapper.RewardComponentMapper
import io.infra.structure.activity.persistence.mapper.RewardComponentPrizeMapper
import io.infra.structure.activity.persistence.mapper.RewardTemplateComponentMapper
import io.infra.structure.activity.persistence.mapper.RewardTemplateMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/** 奖励组件和奖励模板的配置服务。 */
@Service
class RewardConfigurationService(
    private val rewardComponentMapper: RewardComponentMapper,
    private val rewardComponentPrizeMapper: RewardComponentPrizeMapper,
    private val rewardTemplateMapper: RewardTemplateMapper,
    private val rewardTemplateComponentMapper: RewardTemplateComponentMapper,
    private val activityTemplateRewardTemplateMapper: ActivityTemplateRewardTemplateMapper,
    private val prizeComponentConfigurationService: PrizeComponentConfigurationService,
    private val objectMapper: ObjectMapper
) {

    /** 返回全部奖励组件。 */
    fun listComponents(): List<RewardComponentResponse> = rewardComponentMapper
        .selectListByQuery(QueryWrapper.create().orderBy("id"))
        .orEmpty()
        .map(::componentResponse)

    /** 新建奖励组件。 */
    @Transactional
    fun createComponent(request: CreateRewardComponentRequest): RewardComponentResponse {
        validateCode(request.code, "奖励组件编码")
        require(request.name.isNotBlank()) { "奖励组件名称不能为空" }
        validateRewardComponentConfiguration(request)
        require(rewardComponentMapper.selectOneByQuery(QueryWrapper.create().eq("code", request.code.trim())) == null) {
            "奖励组件编码已存在"
        }
        val now = System.currentTimeMillis()
        val entity = RewardComponentEntity(
            code = request.code.trim(),
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf(String::isNotBlank),
            definitionJson = objectMapper.writeValueAsString(request.definition),
            directPrizeMount = request.directPrizeMount,
            enabled = request.enabled,
            createTime = now,
            updateTime = now
        )
        rewardComponentMapper.insert(entity)
        saveComponentPrizeBindings(requireNotNull(entity.id), request.prizes)
        return componentResponse(entity)
    }

    /** 更新奖励组件；编码固定以保证已保存活动的字段路径稳定。 */
    @Transactional
    fun updateComponent(componentId: Long, request: CreateRewardComponentRequest): RewardComponentResponse {
        val entity = requiredComponent(componentId)
        require(request.code.trim() == entity.code) { "奖励组件编码创建后不能修改" }
        require(request.name.isNotBlank()) { "奖励组件名称不能为空" }
        val existingPrizeComponentIdsByMountKey = rewardComponentPrizeMapper
            .selectListByQuery(QueryWrapper.create().eq("component_id", componentId))
            .orEmpty()
            .associate { it.mountKey to it.prizeComponentId }
        validateRewardComponentConfiguration(request, existingPrizeComponentIdsByMountKey)
        entity.name = request.name.trim()
        entity.description = request.description?.trim()?.takeIf(String::isNotBlank)
        entity.definitionJson = objectMapper.writeValueAsString(request.definition)
        entity.directPrizeMount = request.directPrizeMount
        entity.enabled = request.enabled
        entity.updateTime = System.currentTimeMillis()
        require(rewardComponentMapper.update(entity) == 1) { "奖励组件更新失败" }
        rewardComponentPrizeMapper.deleteByQuery(QueryWrapper.create().eq("component_id", componentId))
        saveComponentPrizeBindings(componentId, request.prizes)
        return componentResponse(entity)
    }

    /** 删除未被奖励模板引用的奖励组件。 */
    @Transactional
    fun deleteComponent(componentId: Long) {
        val component = requiredComponent(componentId)
        require(rewardTemplateComponentMapper.selectOneByQuery(QueryWrapper.create().eq("component_id", componentId)) == null) {
            "奖励组件 ${component.name} 已被奖励模板引用，不能删除"
        }
        rewardComponentPrizeMapper.deleteByQuery(QueryWrapper.create().eq("component_id", componentId))
        require(rewardComponentMapper.deleteById(componentId) == 1) { "奖励组件删除失败" }
    }

    /** 保存奖励组件内奖品组件的挂载记录。 */
    private fun saveComponentPrizeBindings(componentId: Long, prizes: List<RewardComponentPrizeRequest>) {
        prizes.forEachIndexed { index, prize ->
            rewardComponentPrizeMapper.insert(
                RewardComponentPrizeEntity(
                    componentId = componentId,
                    prizeComponentId = prize.prizeComponentId,
                    mountKey = prize.mountKey.trim(),
                    mountTitle = prize.mountTitle.trim(),
                    mountMode = prize.mountMode.name,
                    arraySize = prize.arraySize,
                    sortNo = index + 1,
                    required = prize.required
                )
            )
        }
    }

    /** 返回全部奖励模板。 */
    fun listTemplates(): List<RewardTemplateResponse> = rewardTemplateMapper
        .selectListByQuery(QueryWrapper.create().orderBy("id"))
        .orEmpty()
        .map(::templateResponse)

    /** 新建奖励模板并保存奖励组件编排。 */
    @Transactional
    fun createTemplate(request: CreateRewardTemplateRequest): RewardTemplateResponse {
        validateCode(request.code, "奖励模板编码")
        require(request.name.isNotBlank()) { "奖励模板名称不能为空" }
        val components = validateTemplate(request)
        require(rewardTemplateMapper.selectOneByQuery(QueryWrapper.create().eq("code", request.code.trim())) == null) {
            "奖励模板编码已存在"
        }
        val now = System.currentTimeMillis()
        val entity = RewardTemplateEntity(
            code = request.code.trim(),
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf(String::isNotBlank),
            enabled = request.enabled,
            createTime = now,
            updateTime = now
        )
        rewardTemplateMapper.insert(entity)
        saveTemplateBindings(requireNotNull(entity.id), request, components)
        return templateResponse(entity)
    }

    /** 更新奖励模板及其编排；编码固定以保证活动模板中的引用稳定。 */
    @Transactional
    fun updateTemplate(templateId: Long, request: CreateRewardTemplateRequest): RewardTemplateResponse {
        val entity = requiredTemplate(templateId)
        require(request.code.trim() == entity.code) { "奖励模板编码创建后不能修改" }
        require(request.name.isNotBlank()) { "奖励模板名称不能为空" }
        val existingComponentIds = rewardTemplateComponentMapper
            .selectListByQuery(QueryWrapper.create().eq("template_id", templateId))
            .orEmpty()
            .map { it.componentId }
            .toSet()
        val components = validateTemplate(request, existingComponentIds)
        entity.name = request.name.trim()
        entity.description = request.description?.trim()?.takeIf(String::isNotBlank)
        entity.enabled = request.enabled
        entity.updateTime = System.currentTimeMillis()
        require(rewardTemplateMapper.update(entity) == 1) { "奖励模板更新失败" }
        rewardTemplateComponentMapper.deleteByQuery(QueryWrapper.create().eq("template_id", templateId))
        saveTemplateBindings(templateId, request, components)
        return templateResponse(entity)
    }

    /** 删除未被活动模板引用的奖励模板。 */
    @Transactional
    fun deleteTemplate(templateId: Long) {
        val template = requiredTemplate(templateId)
        require(activityTemplateRewardTemplateMapper.selectOneByQuery(QueryWrapper.create().eq("reward_template_id", templateId)) == null) {
            "奖励模板 ${template.name} 已被活动模板引用，不能删除"
        }
        rewardTemplateComponentMapper.deleteByQuery(QueryWrapper.create().eq("template_id", templateId))
        require(rewardTemplateMapper.deleteById(templateId) == 1) { "奖励模板删除失败" }
    }

    /** 读取一个奖励模板；活动模板服务会用它展开活动动态表单。 */
    fun getTemplate(templateId: Long): RewardTemplateResponse = templateResponse(requiredTemplate(templateId))

    /** 校验活动模板是否可以引用奖励模板，并返回其详情。 */
    fun selectableTemplate(templateId: Long, allowDisabled: Boolean): RewardTemplateResponse {
        val template = requiredTemplate(templateId)
        require(template.enabled || allowDisabled) { "奖励模板 ${template.code} 已停用，不能用于活动模板" }
        return templateResponse(template)
    }

    /** 将奖励模板展开为一个顶层动态字段；直接挂载奖品的奖励组件会移除中间组件层级。 */
    fun formField(
        template: RewardTemplateResponse,
        mountKey: String,
        mountTitle: String,
        required: Boolean
    ): ActivityFormField {
        val componentFields = template.components.flatMap { binding ->
            val componentKey = "$mountKey.${binding.mountKey}"
            val componentRequired = required || binding.required
            if (binding.component.directPrizeMount) {
                binding.component.prizes.map { prize ->
                    prizeFormField(prize, mountKey, componentRequired, 1)
                }
            } else {
                listOf(
                    ActivityFormField(
                        key = componentKey,
                        label = binding.mountTitle,
                        type = ComponentNodeType.COMPONENT,
                        required = componentRequired,
                        placeholder = null,
                        defaultValue = null,
                        options = emptyList(),
                        collection = binding.mountMode == ComponentReferenceMode.ARRAY,
                        children = flattenNodes(
                            nodes = binding.component.definition.nodes,
                            parentPath = componentKey,
                            componentRequired = componentRequired,
                            depth = 2,
                            linkRootPath = componentKey
                        ) + binding.component.prizes.map { prize -> prizeFormField(prize, componentKey, componentRequired, 2) },
                        depth = 1
                    )
                )
            }
        }
        return ActivityFormField(
            key = mountKey,
            label = mountTitle,
            type = ComponentNodeType.COMPONENT,
            required = required,
            placeholder = null,
            defaultValue = null,
            options = emptyList(),
            children = componentFields,
            depth = 0
        )
    }

    /** 按直接父路径构建奖品动态字段，并展开所选奖品类型的扩展字段。 */
    private fun prizeFormField(
        prize: RewardComponentPrizeResponse,
        parentPath: String,
        componentRequired: Boolean,
        depth: Int
    ): ActivityFormField {
        val prizeRequired = componentRequired || prize.required
        val prizeKey = "$parentPath.${prize.mountKey}"
        return ActivityFormField(
            key = prizeKey,
            label = prize.mountTitle,
            type = ComponentNodeType.PRIZE,
            required = prizeRequired,
            placeholder = null,
            defaultValue = null,
            options = emptyList(),
            collection = prize.mountMode == ComponentReferenceMode.ARRAY,
            collectionSize = prize.arraySize,
            children = flattenNodes(
                nodes = prize.prizeComponent.definition.nodes,
                parentPath = prizeKey,
                componentRequired = prizeRequired,
                depth = depth + 1,
                linkRootPath = prizeKey
            ),
            depth = depth
        )
    }

    /** 保存奖励模板内的奖励组件挂载记录。 */
    private fun saveTemplateBindings(
        templateId: Long,
        request: CreateRewardTemplateRequest,
        components: List<RewardComponentEntity>
    ) {
        request.components.forEachIndexed { index, binding ->
            rewardTemplateComponentMapper.insert(
                RewardTemplateComponentEntity(
                    templateId = templateId,
                    componentId = requireNotNull(components[index].id),
                    mountKey = binding.mountKey.trim(),
                    mountTitle = binding.mountTitle.trim(),
                    mountMode = binding.mountMode.name,
                    sortNo = index + 1,
                    required = binding.required
                )
            )
        }
    }

    /** 校验奖励模板挂载配置，并返回按请求顺序读取的奖励组件。 */
    private fun validateTemplate(
        request: CreateRewardTemplateRequest,
        allowedDisabledComponentIds: Set<Long> = emptySet()
    ): List<RewardComponentEntity> {
        require(request.components.isNotEmpty()) { "奖励模板至少需要配置一个奖励组件" }
        val mountKeys = request.components.map { it.mountKey }
        mountKeys.forEach { validateCode(it, "挂载键") }
        require(mountKeys.toSet().size == mountKeys.size) { "同一奖励模板中的挂载键不能重复" }
        val titles = request.components.map { it.mountTitle }
        require(titles.all { it.isNotBlank() && it.trim().length <= 128 }) { "挂载标题不能为空且不能超过 128 个字符" }
        val components = request.components.map { binding ->
            requiredComponent(binding.componentId).also { component ->
                require(component.enabled || binding.componentId in allowedDisabledComponentIds) {
                    "奖励组件 ${component.code} 已停用，不能用于奖励模板"
                }
            }
        }
        components.forEachIndexed { index, component ->
            if (component.directPrizeMount) {
                require(request.components[index].mountMode == ComponentReferenceMode.SINGLE) {
                    "直接挂载奖品的奖励组件不能配置为数组"
                }
            }
        }
        return components
    }

    /** 校验奖励组件的普通输入项和奖品组件编排。 */
    private fun validateRewardComponentConfiguration(
        request: CreateRewardComponentRequest,
        existingPrizeComponentIdsByMountKey: Map<String, Long> = emptyMap()
    ) {
        require(request.definition.nodes.isNotEmpty() || request.prizes.isNotEmpty()) {
            "奖励组件至少需要配置一个输入项、分组或奖品组件"
        }
        if (request.directPrizeMount) {
            require(request.definition.nodes.isEmpty()) { "直接挂载奖品时不能配置普通输入节点" }
            require(request.prizes.isNotEmpty()) { "直接挂载奖品时至少需要配置一个奖品组件" }
        }
        if (request.definition.nodes.isNotEmpty()) {
            validateNodes(request.definition.nodes)
            validateLinkRules(request.definition.nodes)
        }
        val keys = request.definition.nodes.map { it.key } + request.prizes.map { it.mountKey }
        keys.forEach { validateCode(it, "字段键") }
        require(keys.toSet().size == keys.size) { "同一奖励组件中的字段键和奖品挂载键不能重复" }
        require(request.prizes.all { it.mountTitle.isNotBlank() && it.mountTitle.trim().length <= 128 }) {
            "奖品挂载标题不能为空且不能超过 128 个字符"
        }
        request.prizes.forEach { prize ->
            prizeComponentConfigurationService.selectableComponent(
                prize.prizeComponentId,
                existingPrizeComponentIdsByMountKey[prize.mountKey.trim()] == prize.prizeComponentId
            )
            if (prize.mountMode == ComponentReferenceMode.SINGLE) {
                require(prize.arraySize == null) { "单个奖品不能配置数组长度" }
            } else if (prize.arraySize != null) {
                require(prize.arraySize in 1..1000) { "奖品数组长度只能在 1 到 1000 之间" }
            }
        }
    }

    /** 递归校验奖励组件节点。 */
    private fun validateNodes(nodes: List<ComponentNode>) {
        require(nodes.map { it.key }.toSet().size == nodes.size) { "同一奖励组件层级中字段键不能重复" }
        nodes.forEach { node ->
            validateCode(node.key, "字段键")
            require(node.label.isNotBlank()) { "字段标题不能为空" }
            require(node.type != ComponentNodeType.COMPONENT && node.type != ComponentNodeType.PRIZE) {
                "奖励组件不能引用其他组件，奖品组件请使用专用配置区添加"
            }
            require(node.componentId == null) { "奖励组件不能引用其他组件" }
            if (node.type == ComponentNodeType.SELECT || node.type == ComponentNodeType.MULTI_SELECT) {
                validateOptions(node.options, node)
            }
            if (node.type == ComponentNodeType.GROUP) {
                require(node.children.isNotEmpty()) { "分组字段 ${node.key} 至少需要一个子字段" }
                require(node.defaultValue.isNullOrBlank()) { "分组字段 ${node.key} 不能配置默认值" }
            }
            if (node.type == ComponentNodeType.NUMBER && !node.defaultValue.isNullOrBlank()) {
                require(node.defaultValue.toBigDecimalOrNull() != null) { "数字字段 ${node.key} 的默认值必须是数字" }
            }
            if (node.type == ComponentNodeType.DATE && !node.defaultValue.isNullOrBlank()) {
                require(runCatching { LocalDate.parse(node.defaultValue) }.isSuccess) { "日期字段 ${node.key} 的默认值必须是 yyyy-MM-dd" }
            }
            if (node.type == ComponentNodeType.DATE_TIME && !node.defaultValue.isNullOrBlank()) {
                require(runCatching { LocalDateTime.parse(node.defaultValue) }.isSuccess) { "日期时间字段 ${node.key} 的默认值必须是 yyyy-MM-ddTHH:mm" }
            }
            validateNodes(node.children)
        }
    }

    /** 联动字段只能指向同一奖励组件定义中的可比较输入项。 */
    private fun validateLinkRules(nodes: List<ComponentNode>) {
        val fields = linkedFieldPaths(nodes)
        fields.forEach { (sourceKey, source) ->
            require(source.linkRules.map { it.targetKey }.toSet().size == source.linkRules.size) { "字段 $sourceKey 的联动目标不能重复" }
            require(source.linkRules.isEmpty() || source.type !in setOf(ComponentNodeType.GROUP, ComponentNodeType.COMPONENT, ComponentNodeType.PRIZE, ComponentNodeType.MULTI_SELECT)) {
                "字段 $sourceKey 不能配置联动规则"
            }
            source.linkRules.forEach { rule ->
                val target = fields[rule.targetKey]
                require(target != null && rule.targetKey != sourceKey && target.type == source.type) { "字段 $sourceKey 的联动目标无效或类型不一致" }
                if (rule.operator !in setOf(ComponentLinkOperator.EQUAL, ComponentLinkOperator.NOT_EQUAL)) {
                    require(source.type in setOf(ComponentNodeType.NUMBER, ComponentNodeType.DATE, ComponentNodeType.DATE_TIME)) { "字段 $sourceKey 仅数字、日期和日期时间支持大小比较" }
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

    /** 校验下拉候选项和默认值。 */
    private fun validateOptions(options: List<ComponentOption>, node: ComponentNode) {
        require(options.isNotEmpty()) { "下拉字段 ${node.key} 至少需要一个候选项" }
        require(options.all { it.value.isNotBlank() && it.label.isNotBlank() }) { "下拉候选项的值和名称不能为空" }
        require(options.map { it.value }.toSet().size == options.size) { "下拉候选项的值不能重复" }
        val defaultValues = if (node.type == ComponentNodeType.MULTI_SELECT) {
            node.defaultValue?.split(',')?.filter(String::isNotBlank).orEmpty()
        } else {
            listOfNotNull(node.defaultValue)
        }
        require(defaultValues.all { defaultValue -> options.any { it.value == defaultValue } }) {
            "下拉字段 ${node.key} 的默认值必须是候选项之一"
        }
    }

    /** 将奖励组件节点展平为活动动态字段。 */
    private fun flattenNodes(
        nodes: List<ComponentNode>,
        parentPath: String,
        componentRequired: Boolean,
        depth: Int,
        linkRootPath: String = parentPath
    ): List<ActivityFormField> = nodes.map { node ->
        val key = "$parentPath.${node.key}"
        val required = componentRequired || node.required
        ActivityFormField(
            key = key,
            label = node.label,
            type = node.type,
            required = node.type != ComponentNodeType.GROUP && required,
            uniqueInArray = node.uniqueInArray,
            linkRules = node.linkRules.map { rule -> rule.copy(targetKey = "$linkRootPath.${rule.targetKey}") },
            placeholder = node.placeholder,
            defaultValue = node.defaultValue,
            options = node.options,
            children = flattenNodes(node.children, key, required, depth + 1, linkRootPath),
            depth = depth
        )
    }

    /** 将奖励组件实体转换为接口视图。 */
    private fun componentResponse(entity: RewardComponentEntity): RewardComponentResponse {
        val prizes = rewardComponentPrizeMapper
            .selectListByQuery(QueryWrapper.create().eq("component_id", requireNotNull(entity.id)).orderBy("sort_no"))
            .orEmpty()
        val prizeComponents = prizeComponentConfigurationService.componentsByIds(prizes.map { it.prizeComponentId }.toSet())
        return RewardComponentResponse(
            id = requireNotNull(entity.id),
            code = entity.code,
            name = entity.name,
            description = entity.description,
            definition = objectMapper.readValue(entity.definitionJson, ComponentDefinition::class.java),
            prizes = prizes.map { prize -> prizeResponse(prize, prizeComponents) },
            directPrizeMount = entity.directPrizeMount,
            enabled = entity.enabled
        )
    }

    /** 将奖品组件挂载记录转换为包含具体奖品类型定义的接口视图。 */
    private fun prizeResponse(
        prize: RewardComponentPrizeEntity,
        prizeComponents: Map<Long, PrizeComponentResponse>
    ): RewardComponentPrizeResponse = RewardComponentPrizeResponse(
        id = requireNotNull(prize.id),
        prizeComponentId = prize.prizeComponentId,
        prizeComponent = prizeComponents[prize.prizeComponentId]
            ?: throw IllegalArgumentException("奖品组件不存在：${prize.prizeComponentId}"),
        sortNo = prize.sortNo,
        mountKey = prize.mountKey,
        mountTitle = prize.mountTitle,
        mountMode = ComponentReferenceMode.valueOf(prize.mountMode),
        arraySize = prize.arraySize,
        required = prize.required
    )

    /** 将奖励模板实体和关联记录转换为接口视图。 */
    private fun templateResponse(entity: RewardTemplateEntity): RewardTemplateResponse {
        val templateId = requireNotNull(entity.id)
        val components = rewardTemplateComponentMapper
            .selectListByQuery(QueryWrapper.create().eq("template_id", templateId).orderBy("sort_no"))
            .orEmpty()
            .map { binding ->
                RewardTemplateComponentResponse(
                    id = requireNotNull(binding.id),
                    sortNo = binding.sortNo,
                    mountKey = binding.mountKey,
                    mountTitle = binding.mountTitle,
                    mountMode = ComponentReferenceMode.valueOf(binding.mountMode),
                    required = binding.required,
                    component = componentResponse(requiredComponent(binding.componentId))
                )
            }
        return RewardTemplateResponse(templateId, entity.code, entity.name, entity.description, entity.enabled, components)
    }

    /** 查询不存在时抛出带业务语义的异常。 */
    private fun requiredComponent(componentId: Long): RewardComponentEntity = rewardComponentMapper.selectOneById(componentId)
        ?: throw IllegalArgumentException("奖励组件不存在：$componentId")

    /** 查询不存在时抛出带业务语义的异常。 */
    private fun requiredTemplate(templateId: Long): RewardTemplateEntity = rewardTemplateMapper.selectOneById(templateId)
        ?: throw IllegalArgumentException("奖励模板不存在：$templateId")

    /** 校验编码和挂载键采用小写字母、数字和下划线。 */
    private fun validateCode(value: String, fieldName: String) {
        require(CODE_PATTERN.matches(value)) { "$fieldName 只能包含小写字母、数字和下划线" }
    }

    private companion object {
        /** 编码和字段键的允许格式。 */
        val CODE_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")
    }
}
