package io.infra.structure.activity.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.mybatisflex.core.query.QueryWrapper
import io.infra.structure.activity.admin.domain.model.ComponentDefinition
import io.infra.structure.activity.admin.domain.model.ComponentLinkOperator
import io.infra.structure.activity.admin.domain.model.ComponentNode
import io.infra.structure.activity.admin.domain.model.ComponentNodeType
import io.infra.structure.activity.admin.domain.model.ComponentOption
import io.infra.structure.activity.admin.domain.model.ComponentReferenceMode
import io.infra.structure.activity.admin.domain.model.PrizeComponentType
import io.infra.structure.activity.admin.dto.CreatePrizeComponentRequest
import io.infra.structure.activity.admin.dto.PrizeComponentResponse
import io.infra.structure.activity.persistence.entity.PrizeComponentEntity
import io.infra.structure.activity.persistence.mapper.PrizeComponentMapper
import io.infra.structure.activity.persistence.mapper.RewardComponentPrizeMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/** 奖品组件配置服务，负责维护固定奖品组件和业务扩展奖品组件。 */
@Service
class PrizeComponentConfigurationService(
    private val prizeComponentMapper: PrizeComponentMapper,
    private val rewardComponentPrizeMapper: RewardComponentPrizeMapper,
    private val objectMapper: ObjectMapper
) {

    /** 返回全部奖品组件，固定奖品组件始终排在首位。 */
    fun listComponents(): List<PrizeComponentResponse> = prizeComponentMapper
        .selectListByQuery(QueryWrapper.create().orderBy("id"))
        .orEmpty()
        .map(::response)

    /** 新建一个在固定奖品字段基础上增加自定义字段的扩展奖品组件。 */
    @Transactional
    fun createComponent(request: CreatePrizeComponentRequest): PrizeComponentResponse {
        validateCode(request.code, "奖品扩展编码")
        require(request.name.isNotBlank()) { "奖品扩展名称不能为空" }
        validateDefinition(request.definition)
        require(prizeComponentMapper.selectOneByQuery(QueryWrapper.create().eq("code", request.code.trim())) == null) {
            "奖品扩展编码已存在"
        }
        val now = System.currentTimeMillis()
        val entity = PrizeComponentEntity(
            type = PrizeComponentType.EXTENSION.name,
            code = request.code.trim(),
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf(String::isNotBlank),
            definitionJson = objectMapper.writeValueAsString(request.definition),
            enabled = request.enabled,
            createTime = now,
            updateTime = now
        )
        prizeComponentMapper.insert(entity)
        return response(entity)
    }

    /** 更新扩展奖品组件；固定奖品组件和编码均不可变更。 */
    @Transactional
    fun updateComponent(componentId: Long, request: CreatePrizeComponentRequest): PrizeComponentResponse {
        val entity = requiredComponent(componentId)
        require(entity.id != FIXED_COMPONENT_ID) { "固定奖品组件不能修改" }
        require(PrizeComponentType.valueOf(entity.type) == PrizeComponentType.EXTENSION) { "仅支持修改扩展奖品组件" }
        require(request.code.trim() == entity.code) { "奖品扩展编码创建后不能修改" }
        require(request.name.isNotBlank()) { "奖品扩展名称不能为空" }
        validateDefinition(request.definition)
        entity.name = request.name.trim()
        entity.description = request.description?.trim()?.takeIf(String::isNotBlank)
        entity.definitionJson = objectMapper.writeValueAsString(request.definition)
        entity.enabled = request.enabled
        entity.updateTime = System.currentTimeMillis()
        require(prizeComponentMapper.update(entity) == 1) { "奖品扩展更新失败" }
        return response(entity)
    }

    /** 删除未被奖励组件挂载的扩展奖品组件。 */
    @Transactional
    fun deleteComponent(componentId: Long) {
        val entity = requiredComponent(componentId)
        require(entity.id != FIXED_COMPONENT_ID) { "固定奖品组件不能删除" }
        require(rewardComponentPrizeMapper.selectOneByQuery(QueryWrapper.create().eq("prize_component_id", componentId)) == null) {
            "奖品扩展 ${entity.name} 已被奖励组件挂载，不能删除"
        }
        require(prizeComponentMapper.deleteById(componentId) == 1) { "奖品扩展删除失败" }
    }

    /** 查询指定奖品组件，并校验其可被新的奖励组件使用。 */
    fun selectableComponent(componentId: Long, allowDisabled: Boolean): PrizeComponentResponse {
        val entity = requiredComponent(componentId)
        require(entity.enabled || allowDisabled || entity.id == FIXED_COMPONENT_ID) {
            "奖品组件 ${entity.code} 已停用，不能用于奖励组件"
        }
        return response(entity)
    }

    /** 批量查询奖品组件，供奖励组件详情组装避免逐条查询。 */
    fun componentsByIds(componentIds: Set<Long>): Map<Long, PrizeComponentResponse> {
        if (componentIds.isEmpty()) {
            return emptyMap()
        }
        return prizeComponentMapper.selectListByQuery(QueryWrapper.create().`in`("id", componentIds))
            .orEmpty()
            .map(::response)
            .associateBy(PrizeComponentResponse::id)
    }

    /** 校验扩展字段定义只能使用普通输入和分组，且至少定义一个字段。 */
    private fun validateDefinition(definition: ComponentDefinition) {
        require(definition.nodes.isNotEmpty()) { "奖品扩展至少需要配置一个输入项或分组" }
        require(definition.nodes.none { it.key in FIXED_PROPERTY_KEYS }) {
            "奖品扩展的根字段键不能与固定奖品字段重复"
        }
        validateNodes(definition.nodes)
        validateLinkRules(definition.nodes)
    }

    /** 递归校验扩展奖品字段节点。 */
    private fun validateNodes(nodes: List<ComponentNode>) {
        require(nodes.map { it.key }.toSet().size == nodes.size) { "同一奖品扩展层级中字段键不能重复" }
        nodes.forEach { node ->
            validateCode(node.key, "字段键")
            require(node.label.isNotBlank()) { "字段标题不能为空" }
            require(node.type != ComponentNodeType.COMPONENT && node.type != ComponentNodeType.PRIZE) {
                "奖品扩展只能配置普通输入项或分组"
            }
            require(node.componentId == null) { "奖品扩展不能引用其他组件" }
            require(node.componentMode == ComponentReferenceMode.SINGLE) { "奖品扩展不能配置组件数组形式" }
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

    /** 联动字段只能指向同一奖品扩展定义中的可比较输入项。 */
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

    /** 将实体转换为接口视图。 */
    private fun response(entity: PrizeComponentEntity): PrizeComponentResponse = PrizeComponentResponse(
        id = requireNotNull(entity.id),
        type = PrizeComponentType.valueOf(entity.type),
        code = entity.code,
        name = entity.name,
        description = entity.description,
        definition = objectMapper.readValue(entity.definitionJson, ComponentDefinition::class.java),
        enabled = entity.enabled
    )

    /** 查询不存在时抛出带业务语义的异常。 */
    private fun requiredComponent(componentId: Long): PrizeComponentEntity = prizeComponentMapper.selectOneById(componentId)
        ?: throw IllegalArgumentException("奖品组件不存在：$componentId")

    /** 校验编码和字段键采用小写字母、数字和下划线。 */
    private fun validateCode(value: String, fieldName: String) {
        require(CODE_PATTERN.matches(value)) { "$fieldName 只能包含小写字母、数字和下划线" }
    }

    private companion object {
        /** 固定奖品组件的不可变主键。 */
        const val FIXED_COMPONENT_ID = 1L

        /** 编码和字段键的允许格式。 */
        val CODE_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")

        /** 固定奖品字段使用的顶层属性键。 */
        val FIXED_PROPERTY_KEYS = setOf(
            "prizeType",
            "prizeId",
            "prizeName",
            "prizeIcon",
            "prizeValue",
            "prizeDisplayValue",
            "prizeQuantity"
        )
    }
}
