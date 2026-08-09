package io.infra.structure.activity.admin.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.mybatisflex.core.query.QueryWrapper
import io.infra.structure.activity.admin.domain.model.ActivityTaskExecutionStatus
import io.infra.structure.activity.admin.domain.model.ActivityTaskHandlerType
import io.infra.structure.activity.admin.domain.model.ActivityTaskStatus
import io.infra.structure.activity.admin.domain.model.ActivityTaskTriggerConfig
import io.infra.structure.activity.admin.domain.model.ActivityTaskTriggerSource
import io.infra.structure.activity.admin.domain.model.ActivityTaskTriggerType
import io.infra.structure.activity.admin.dto.ActivityTaskExecutionResponse
import io.infra.structure.activity.admin.dto.ActivityTaskCronPreviewResponse
import io.infra.structure.activity.admin.dto.ActivityTaskResponse
import io.infra.structure.activity.admin.dto.ActivityTaskTemplateResponse
import io.infra.structure.activity.admin.dto.ActivityTemplateTaskBindingRequest
import io.infra.structure.activity.admin.dto.ActivityTemplateTaskBindingResponse
import io.infra.structure.activity.admin.dto.CreateActivityTaskTemplateRequest
import io.infra.structure.activity.persistence.entity.ActivityEntity
import io.infra.structure.activity.persistence.entity.ActivityTaskDefinitionEntity
import io.infra.structure.activity.persistence.entity.ActivityTaskExecutionLogEntity
import io.infra.structure.activity.persistence.entity.ActivityTaskInstanceEntity
import io.infra.structure.activity.persistence.entity.ActivityTemplateTaskBindingEntity
import io.infra.structure.activity.persistence.mapper.ActivityMapper
import io.infra.structure.activity.persistence.mapper.ActivityTaskDefinitionMapper
import io.infra.structure.activity.persistence.mapper.ActivityTaskExecutionLogMapper
import io.infra.structure.activity.persistence.mapper.ActivityTaskInstanceMapper
import io.infra.structure.activity.persistence.mapper.ActivityTemplateMapper
import io.infra.structure.activity.persistence.mapper.ActivityTemplateTaskBindingMapper
import io.infra.structure.activity.admin.task.ActivityTaskExecutionContext
import io.infra.structure.activity.admin.task.ActivityTaskHandler
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

/** 活动任务的模板管理、实例生成、分布式调度与执行审计服务。 */
@Service
class ActivityTaskService(
    private val taskDefinitionMapper: ActivityTaskDefinitionMapper,
    private val templateTaskBindingMapper: ActivityTemplateTaskBindingMapper,
    private val taskInstanceMapper: ActivityTaskInstanceMapper,
    private val executionLogMapper: ActivityTaskExecutionLogMapper,
    private val activityMapper: ActivityMapper,
    private val activityTemplateMapper: ActivityTemplateMapper,
    private val objectMapper: ObjectMapper,
    private val taskHandlers: List<ActivityTaskHandler>
) {

    /** 当前应用实例的分布式任务租约标识。 */
    private val instanceId: String = "activity-${UUID.randomUUID()}"

    /** 使用与实际调度一致的 Spring Cron 解析器预览未来五次触发时间。 */
    fun previewCronNextTimes(cronValue: String?, timezone: String?): ActivityTaskCronPreviewResponse {
        require(!cronValue.isNullOrBlank()) { "Cron 表达式不能为空" }
        val cron = CronExpression.parse(cronValue)
        val zone = ZoneId.of(timezone ?: "Asia/Shanghai")
        var cursor = ZonedDateTime.ofInstant(Instant.now(), zone)
        val nextTimes = buildList {
            while (size < 5) {
                val next = cron.next(cursor) ?: break
                add(next.toInstant().toEpochMilli())
                cursor = next
            }
        }
        return ActivityTaskCronPreviewResponse(nextTimes)
    }

    /** 返回全部任务模板。 */
    fun listTaskTemplates(): List<ActivityTaskTemplateResponse> = taskDefinitionMapper
        .selectListByQuery(QueryWrapper.create().orderBy("id"))
        .orEmpty()
        .map(::taskTemplateResponse)

    /** 新建任务模板。 */
    @Transactional
    open fun createTaskTemplate(request: CreateActivityTaskTemplateRequest): ActivityTaskTemplateResponse {
        validateTaskTemplateRequest(request)
        val now = System.currentTimeMillis()
        val entity = ActivityTaskDefinitionEntity(
            code = generateTaskDefinitionCode(request.handlerType),
            name = request.name.trim(),
            handlerType = request.handlerType.name,
            description = request.description?.trim()?.takeIf(String::isNotBlank),
            defaultParametersJson = objectMapper.writeValueAsString(request.defaultParameters),
            maxRetryCount = request.maxRetryCount,
            retryIntervalMillis = request.retryIntervalMillis,
            enabled = request.enabled,
            createTime = now,
            updateTime = now
        )
        taskDefinitionMapper.insert(entity)
        return taskTemplateResponse(entity)
    }

    /** 更新任务模板；内部唯一标识由系统创建时生成，不向管理页面暴露。 */
    @Transactional
    open fun updateTaskTemplate(templateId: Long, request: CreateActivityTaskTemplateRequest): ActivityTaskTemplateResponse {
        val entity = requiredTaskTemplate(templateId)
        validateTaskTemplateRequest(request)
        entity.name = request.name.trim()
        entity.handlerType = request.handlerType.name
        entity.description = request.description?.trim()?.takeIf(String::isNotBlank)
        entity.defaultParametersJson = objectMapper.writeValueAsString(request.defaultParameters)
        entity.maxRetryCount = request.maxRetryCount
        entity.retryIntervalMillis = request.retryIntervalMillis
        entity.enabled = request.enabled
        entity.updateTime = System.currentTimeMillis()
        require(taskDefinitionMapper.update(entity) == 1) { "任务模板更新失败" }
        return taskTemplateResponse(entity)
    }

    /** 删除未被活动模板关联的任务模板。 */
    @Transactional
    open fun deleteTaskTemplate(templateId: Long) {
        val entity = requiredTaskTemplate(templateId)
        require(templateTaskBindingMapper.selectOneByQuery(QueryWrapper.create().eq("task_template_id", templateId)) == null) {
            "任务模板 ${entity.name} 已被活动模板关联，不能删除"
        }
        require(taskDefinitionMapper.deleteById(templateId) == 1) { "任务模板删除失败" }
    }

    /** 返回指定活动模板的全部任务绑定。 */
    fun listTemplateTasks(activityTemplateId: Long): List<ActivityTemplateTaskBindingResponse> {
        require(activityTemplateMapper.selectOneById(activityTemplateId) != null) { "活动模板不存在" }
        return templateTaskBindingMapper.selectListByQuery(
            QueryWrapper.create().eq("activity_template_id", activityTemplateId).orderBy("sort_no")
        ).orEmpty().map(::templateTaskBindingResponse)
    }

    /** 使用提交列表整体替换活动模板的任务绑定。 */
    @Transactional
    open fun replaceTemplateTasks(activityTemplateId: Long, requests: List<ActivityTemplateTaskBindingRequest>): List<ActivityTemplateTaskBindingResponse> {
        require(activityTemplateMapper.selectOneById(activityTemplateId) != null) { "活动模板不存在" }
        validateTemplateTaskBindings(requests)
        templateTaskBindingMapper.deleteByQuery(QueryWrapper.create().eq("activity_template_id", activityTemplateId))
        val now = System.currentTimeMillis()
        requests.forEachIndexed { index, request ->
            val taskDefinitionId = requireNotNull(request.taskTemplateId) { "请选择任务定义" }
            val taskTemplate = requiredTaskTemplate(taskDefinitionId)
            templateTaskBindingMapper.insert(
                ActivityTemplateTaskBindingEntity(
                    activityTemplateId = activityTemplateId,
                    taskTemplateId = taskTemplate.id ?: error("任务模板主键不能为空"),
                    code = request.code.trim(),
                    name = request.name.trim(),
                    handlerType = taskTemplate.handlerType,
                    triggerType = request.triggerType.name,
                    triggerConfigJson = objectMapper.writeValueAsString(request.triggerConfig),
                    parameterOverridesJson = objectMapper.writeValueAsString(request.parameterOverrides),
                    enabled = request.enabled,
                    sortNo = index + 1,
                    createTime = now,
                    updateTime = now
                )
            )
        }
        val bindings = listTemplateTasks(activityTemplateId)
        // 已经上线的活动也必须立即使用新的任务快照，避免模板与实际调度脱节。
        activityMapper.selectListByQuery(QueryWrapper.create().eq("template_id", activityTemplateId))
            .orEmpty()
            .forEach(::refreshActivityTasks)
        return bindings
    }

    /** 根据活动状态和模板绑定生成或刷新实际任务快照。 */
    @Transactional
    open fun refreshActivityTasks(activity: ActivityEntity) {
        val activityId = requireNotNull(activity.id) { "活动主键不能为空" }
        val existingTasks = taskInstanceMapper.selectListByQuery(QueryWrapper.create().eq("activity_id", activityId)).orEmpty()
        if (activity.status != "ACTIVE" || activity.onlineStatus != "ONLINE") {
            existingTasks.filter { it.status == ActivityTaskStatus.PENDING.name }.forEach { task ->
                task.status = ActivityTaskStatus.CANCELLED.name
                task.nextTriggerTime = null
                task.leaseOwner = null
                task.leaseExpireTime = null
                task.updateTime = System.currentTimeMillis()
                taskInstanceMapper.update(task)
            }
            return
        }

        val bindings = templateTaskBindingMapper.selectListByQuery(
            QueryWrapper.create().eq("activity_template_id", activity.templateId).orderBy("sort_no")
        ).orEmpty().filter { it.enabled }
        val activeCodes = bindings.map { it.code }.toSet()
        existingTasks.filter { it.code !in activeCodes && it.status == ActivityTaskStatus.PENDING.name }.forEach { task ->
            task.status = ActivityTaskStatus.CANCELLED.name
            task.nextTriggerTime = null
            task.updateTime = System.currentTimeMillis()
            taskInstanceMapper.update(task)
        }

        val existingByCode = existingTasks.associateBy { it.code }
        bindings.forEach { binding ->
            val taskTemplate = requiredTaskTemplate(binding.taskTemplateId)
            val task = existingByCode[binding.code] ?: ActivityTaskInstanceEntity(
                activityId = activityId,
                activityTemplateTaskId = requireNotNull(binding.id) { "模板任务绑定主键不能为空" },
                taskTemplateId = binding.taskTemplateId,
                code = binding.code,
                name = binding.name,
                handlerType = binding.handlerType,
                triggerType = binding.triggerType,
                triggerConfigJson = binding.triggerConfigJson,
                parametersJson = "{}",
                maxRetryCount = taskTemplate.maxRetryCount,
                retryIntervalMillis = taskTemplate.retryIntervalMillis,
                createTime = System.currentTimeMillis()
            )
            val parameters = linkedMapOf<String, Any?>()
            parameters.putAll(readMap(taskTemplate.defaultParametersJson))
            parameters.putAll(readMap(binding.parameterOverridesJson))
            val triggerConfig = objectMapper.readValue(binding.triggerConfigJson, ActivityTaskTriggerConfig::class.java)
            task.activityTemplateTaskId = requireNotNull(binding.id) { "模板任务绑定主键不能为空" }
            task.taskTemplateId = binding.taskTemplateId
            task.name = binding.name
            task.handlerType = binding.handlerType
            task.triggerType = binding.triggerType
            task.triggerConfigJson = binding.triggerConfigJson
            task.parametersJson = objectMapper.writeValueAsString(parameters)
            task.maxRetryCount = taskTemplate.maxRetryCount
            task.retryIntervalMillis = taskTemplate.retryIntervalMillis
            task.nextTriggerTime = nextTriggerTime(activity, ActivityTaskTriggerType.valueOf(binding.triggerType), triggerConfig, System.currentTimeMillis())
            task.status = if (ActivityTaskTriggerType.valueOf(binding.triggerType) == ActivityTaskTriggerType.MANUAL || task.nextTriggerTime != null) {
                ActivityTaskStatus.PENDING.name
            } else {
                ActivityTaskStatus.COMPLETED.name
            }
            task.leaseOwner = null
            task.leaseExpireTime = null
            task.retryCount = 0
            task.updateTime = System.currentTimeMillis()
            if (task.id == null) {
                taskInstanceMapper.insert(task)
            } else {
                taskInstanceMapper.update(task)
            }
        }
    }

    /** 返回活动实例生成的全部任务。 */
    fun listActivityTasks(activityId: Long): List<ActivityTaskResponse> = taskInstanceMapper
        .selectListByQuery(QueryWrapper.create().eq("activity_id", activityId).orderBy("id"))
        .orEmpty()
        .map(::activityTaskResponse)

    /** 返回任务的最近执行记录。 */
    fun listTaskExecutions(activityTaskId: Long): List<ActivityTaskExecutionResponse> = executionLogMapper
        .selectListByQuery(QueryWrapper.create().eq("activity_task_id", activityTaskId).orderBy("id", false))
        .orEmpty()
        .map(::executionResponse)

    /** 手动立即执行一个任务，不改写其自动调度时间。 */
    @Transactional
    open fun triggerManually(activityTaskId: Long, reason: String?): ActivityTaskExecutionResponse {
        val task = requiredActivityTask(activityTaskId)
        require(task.status != ActivityTaskStatus.CANCELLED.name) { "已取消的任务不能手动触发" }
        return executeTask(
            task = task,
            triggerSource = ActivityTaskTriggerSource.MANUAL,
            triggerTime = System.currentTimeMillis(),
            executionKey = "${task.id}:manual:${UUID.randomUUID()}",
            reason = reason?.trim()?.takeIf(String::isNotBlank),
            updateSchedule = false
        )
    }

    /** 扫描并抢占到期任务；条件更新保证多实例中仅一个实例获得租约。 */
    fun executeDueTasks() {
        val now = System.currentTimeMillis()
        val dueTasks = taskInstanceMapper.selectListByQuery(
            QueryWrapper.create().eq("status", ActivityTaskStatus.PENDING.name).le("next_trigger_time", now).orderBy("next_trigger_time")
        ).orEmpty()
        dueTasks.forEach { candidate ->
            val taskId = candidate.id ?: return@forEach
            if (taskInstanceMapper.claimDueTask(taskId, instanceId, now + LEASE_MILLIS, now) == 1) {
                val task = requiredActivityTask(taskId)
                executeTask(
                    task = task,
                    triggerSource = if (task.retryCount > 0) ActivityTaskTriggerSource.RETRY else ActivityTaskTriggerSource.SCHEDULED,
                    triggerTime = requireNotNull(task.nextTriggerTime) { "到期任务缺少触发时间" },
                    executionKey = "${task.id}:${task.nextTriggerTime}",
                    reason = null,
                    updateSchedule = true
                )
            }
        }
    }

    /** 实际调用处理器，并在成功、失败、重试之间更新任务状态。 */
    private fun executeTask(
        task: ActivityTaskInstanceEntity,
        triggerSource: ActivityTaskTriggerSource,
        triggerTime: Long,
        executionKey: String,
        reason: String?,
        updateSchedule: Boolean
    ): ActivityTaskExecutionResponse {
        val now = System.currentTimeMillis()
        val execution = ActivityTaskExecutionLogEntity(
            activityTaskId = requireNotNull(task.id) { "活动任务主键不能为空" },
            executionKey = executionKey,
            triggerSource = triggerSource.name,
            triggerTime = triggerTime,
            status = ActivityTaskExecutionStatus.RUNNING.name,
            attemptNo = task.retryCount + 1,
            requestJson = objectMapper.writeValueAsString(mapOf("reason" to reason)),
            startTime = now,
            createTime = now,
            updateTime = now
        )
        try {
            executionLogMapper.insert(execution)
        } catch (exception: Exception) {
            return executionLogMapper.selectOneByQuery(QueryWrapper.create().eq("execution_key", executionKey))
                ?.let(::executionResponse)
                ?: throw exception
        }

        try {
            val handlerType = ActivityTaskHandlerType.valueOf(task.handlerType)
            val handler = taskHandlers.firstOrNull { it.supports(handlerType) }
                ?: error("未找到任务处理器：${task.handlerType}")
            val result = handler.execute(
                ActivityTaskExecutionContext(
                    activityId = task.activityId,
                    activityTaskId = requireNotNull(task.id),
                    handlerType = handlerType,
                    parameters = readMap(task.parametersJson),
                    triggerTime = triggerTime
                )
            )
            val endTime = System.currentTimeMillis()
            execution.status = ActivityTaskExecutionStatus.SUCCESS.name
            execution.resultJson = objectMapper.writeValueAsString(result)
            execution.endTime = endTime
            execution.updateTime = endTime
            executionLogMapper.update(execution)
            if (updateSchedule) {
                completeScheduledTask(task, triggerTime, endTime)
            }
        } catch (exception: Exception) {
            val endTime = System.currentTimeMillis()
            execution.status = ActivityTaskExecutionStatus.FAILED.name
            execution.errorMessage = exception.message?.take(1024) ?: exception.javaClass.simpleName
            execution.endTime = endTime
            execution.updateTime = endTime
            executionLogMapper.update(execution)
            if (updateSchedule) {
                retryOrFailScheduledTask(task, endTime)
            }
        }
        return executionResponse(execution)
    }

    /** 成功后计算下一次执行时间，或标记任务完成。 */
    private fun completeScheduledTask(task: ActivityTaskInstanceEntity, triggerTime: Long, now: Long) {
        val activity = requiredActivity(task.activityId)
        val triggerType = ActivityTaskTriggerType.valueOf(task.triggerType)
        val triggerConfig = objectMapper.readValue(task.triggerConfigJson, ActivityTaskTriggerConfig::class.java)
        val nextTime = nextTriggerTime(activity, triggerType, triggerConfig, triggerTime + 1)
        task.lastTriggerTime = triggerTime
        task.nextTriggerTime = nextTime
        task.retryCount = 0
        task.status = if (nextTime == null) ActivityTaskStatus.COMPLETED.name else ActivityTaskStatus.PENDING.name
        task.leaseOwner = null
        task.leaseExpireTime = null
        task.updateTime = now
        taskInstanceMapper.update(task)
    }

    /** 失败后按任务快照决定重试或最终失败。 */
    private fun retryOrFailScheduledTask(task: ActivityTaskInstanceEntity, now: Long) {
        task.retryCount += 1
        task.lastTriggerTime = task.nextTriggerTime
        task.leaseOwner = null
        task.leaseExpireTime = null
        if (task.retryCount <= task.maxRetryCount) {
            task.status = ActivityTaskStatus.PENDING.name
            task.nextTriggerTime = now + task.retryIntervalMillis
        } else {
            task.status = ActivityTaskStatus.FAILED.name
            task.nextTriggerTime = null
        }
        task.updateTime = now
        taskInstanceMapper.update(task)
    }

    /** 按触发配置计算不早于 afterTime 的下一次执行时间。 */
    private fun nextTriggerTime(
        activity: ActivityEntity,
        triggerType: ActivityTaskTriggerType,
        config: ActivityTaskTriggerConfig,
        afterTime: Long
    ): Long? = when (triggerType) {
        ActivityTaskTriggerType.MANUAL -> null
        ActivityTaskTriggerType.FIXED_TIMES -> config.fixedTimes.orEmpty().filter { it >= afterTime }.minOrNull()
        ActivityTaskTriggerType.ACTIVITY_START_OFFSET -> {
            val start = requireNotNull(activity.validStartTime) { "相对活动开始的任务要求活动配置开始时间" }
            (start + requireNotNull(config.offsetMillis) { "相对活动开始任务缺少偏移量" }).takeIf { it >= afterTime }
        }
        ActivityTaskTriggerType.ACTIVITY_END_OFFSET -> {
            val end = requireNotNull(activity.validEndTime) { "相对活动结束的任务要求活动配置结束时间" }
            (end + requireNotNull(config.offsetMillis) { "相对活动结束任务缺少偏移量" }).takeIf { it >= afterTime }
        }
        ActivityTaskTriggerType.INTERVAL_WINDOW -> nextIntervalWindowTime(activity, config, afterTime)
        ActivityTaskTriggerType.CRON -> nextCronTime(activity, config, afterTime)
    }

    /** 计算活动有效期窗口内按固定间隔的下一次时间。 */
    private fun nextIntervalWindowTime(activity: ActivityEntity, config: ActivityTaskTriggerConfig, afterTime: Long): Long? {
        val start = requireNotNull(activity.validStartTime) { "间隔任务要求活动配置开始时间" } + (config.windowStartOffsetMillis ?: 0L)
        val end = requireNotNull(activity.validEndTime) { "间隔任务要求活动配置结束时间" } + (config.windowEndOffsetMillis ?: 0L)
        val interval = requireNotNull(config.intervalMillis) { "间隔任务缺少执行周期" }
        require(interval > 0) { "间隔任务执行周期必须大于 0" }
        val next = if (afterTime <= start) start else start + ((afterTime - start + interval - 1) / interval) * interval
        return next.takeIf { it <= end }
    }

    /** 使用活动时区与有效期边界计算下一次 Cron 触发时间。 */
    private fun nextCronTime(activity: ActivityEntity, config: ActivityTaskTriggerConfig, afterTime: Long): Long? {
        val cron = CronExpression.parse(requireNotNull(config.cron) { "Cron 任务缺少表达式" })
        val zone = ZoneId.of(config.timezone ?: "Asia/Shanghai")
        val next = cron.next(ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterTime), zone))?.toInstant()?.toEpochMilli()
        if (next == null || activity.validForever) {
            return next
        }
        return next.takeIf { candidate ->
            val start = requireNotNull(activity.validStartTime)
            val end = requireNotNull(activity.validEndTime)
            candidate in start..end
        }
    }

    /** 校验任务模板的可执行参数。 */
    private fun validateTaskTemplateRequest(request: CreateActivityTaskTemplateRequest) {
        require(request.name.isNotBlank()) { "任务模板名称不能为空" }
        require(request.maxRetryCount in 0..20) { "最大重试次数只能在 0 到 20 之间" }
        require(request.retryIntervalMillis in 1_000..86_400_000) { "重试间隔只能在 1 秒到 24 小时之间" }
    }

    /** 校验模板内任务编码、关联模板和触发配置。 */
    private fun validateTemplateTaskBindings(requests: List<ActivityTemplateTaskBindingRequest>) {
        val codes = requests.map { it.code.trim() }
        codes.forEach { validateCode(it, "任务编码") }
        require(codes.toSet().size == codes.size) { "同一活动模板中的任务编码不能重复" }
        requests.forEach { request ->
            require(request.name.isNotBlank()) { "任务名称不能为空" }
            val taskDefinitionId = requireNotNull(request.taskTemplateId) { "请选择任务定义" }
            val taskTemplate = requiredTaskTemplate(taskDefinitionId)
            require(taskTemplate.enabled) { "任务模板 ${taskTemplate.name} 已停用，不能关联" }
            validateTriggerConfig(request.triggerType, request.triggerConfig)
        }
    }

    /** 根据触发类型校验必要参数。 */
    private fun validateTriggerConfig(type: ActivityTaskTriggerType, config: ActivityTaskTriggerConfig) {
        when (type) {
            ActivityTaskTriggerType.MANUAL -> Unit
            ActivityTaskTriggerType.FIXED_TIMES -> require(config.fixedTimes.orEmpty().isNotEmpty() && config.fixedTimes.orEmpty().all { it > 0 }) {
                "指定时间触发至少需要一个有效时间"
            }
            ActivityTaskTriggerType.CRON -> {
                require(!config.cron.isNullOrBlank()) { "Cron 触发缺少表达式" }
                CronExpression.parse(config.cron)
                ZoneId.of(config.timezone ?: "Asia/Shanghai")
            }
            ActivityTaskTriggerType.ACTIVITY_START_OFFSET,
            ActivityTaskTriggerType.ACTIVITY_END_OFFSET -> require(config.offsetMillis != null) { "相对活动时间触发缺少偏移量" }
            ActivityTaskTriggerType.INTERVAL_WINDOW -> require(config.intervalMillis != null && config.intervalMillis > 0) {
                "间隔触发缺少有效执行周期"
            }
        }
    }

    /** 校验复用编码仅包含小写字母、数字和下划线。 */
    private fun validateCode(code: String, label: String) {
        require(code.matches(CODE_PATTERN)) { "$label 只能以小写字母开头，且仅包含小写字母、数字和下划线" }
    }

    /** 为不需要人工维护编码的任务定义生成内部唯一标识。 */
    private fun generateTaskDefinitionCode(handlerType: ActivityTaskHandlerType): String {
        while (true) {
            val code = "${handlerType.name.lowercase(Locale.ROOT)}_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            if (taskDefinitionMapper.selectOneByQuery(QueryWrapper.create().eq("code", code)) == null) {
                return code
            }
        }
    }

    /** 读取 JSON 对象；任务参数均以对象形式保存。 */
    private fun readMap(json: String): Map<String, Any?> = objectMapper.readValue(json, MAP_TYPE)

    /** 查询必须存在的任务模板。 */
    private fun requiredTaskTemplate(templateId: Long): ActivityTaskDefinitionEntity =
        requireNotNull(taskDefinitionMapper.selectOneById(templateId)) { "任务模板不存在" }

    /** 查询必须存在的活动任务。 */
    private fun requiredActivityTask(taskId: Long): ActivityTaskInstanceEntity =
        requireNotNull(taskInstanceMapper.selectOneById(taskId)) { "活动任务不存在" }

    /** 查询必须存在的活动。 */
    private fun requiredActivity(activityId: Long): ActivityEntity =
        requireNotNull(activityMapper.selectOneById(activityId)) { "活动不存在" }

    /** 将任务模板实体转换为接口响应。 */
    private fun taskTemplateResponse(entity: ActivityTaskDefinitionEntity): ActivityTaskTemplateResponse = ActivityTaskTemplateResponse(
        id = requireNotNull(entity.id),
        name = entity.name,
        handlerType = ActivityTaskHandlerType.valueOf(entity.handlerType),
        description = entity.description,
        defaultParameters = readMap(entity.defaultParametersJson),
        maxRetryCount = entity.maxRetryCount,
        retryIntervalMillis = entity.retryIntervalMillis,
        enabled = entity.enabled
    )

    /** 将模板任务绑定实体转换为接口响应。 */
    private fun templateTaskBindingResponse(entity: ActivityTemplateTaskBindingEntity): ActivityTemplateTaskBindingResponse = ActivityTemplateTaskBindingResponse(
        id = requireNotNull(entity.id),
        activityTemplateId = entity.activityTemplateId,
        taskTemplateId = entity.taskTemplateId,
        code = entity.code,
        name = entity.name,
        handlerType = ActivityTaskHandlerType.valueOf(entity.handlerType),
        triggerType = ActivityTaskTriggerType.valueOf(entity.triggerType),
        triggerConfig = objectMapper.readValue(entity.triggerConfigJson, ActivityTaskTriggerConfig::class.java),
        parameterOverrides = readMap(entity.parameterOverridesJson),
        enabled = entity.enabled,
        sortNo = entity.sortNo
    )

    /** 将活动任务实体转换为接口响应。 */
    private fun activityTaskResponse(entity: ActivityTaskInstanceEntity): ActivityTaskResponse = ActivityTaskResponse(
        id = requireNotNull(entity.id),
        activityId = entity.activityId,
        activityTemplateTaskId = entity.activityTemplateTaskId,
        code = entity.code,
        name = entity.name,
        handlerType = ActivityTaskHandlerType.valueOf(entity.handlerType),
        triggerType = ActivityTaskTriggerType.valueOf(entity.triggerType),
        nextTriggerTime = entity.nextTriggerTime,
        status = ActivityTaskStatus.valueOf(entity.status),
        retryCount = entity.retryCount,
        lastTriggerTime = entity.lastTriggerTime
    )

    /** 将执行记录实体转换为接口响应。 */
    private fun executionResponse(entity: ActivityTaskExecutionLogEntity): ActivityTaskExecutionResponse = ActivityTaskExecutionResponse(
        id = requireNotNull(entity.id),
        activityTaskId = entity.activityTaskId,
        executionKey = entity.executionKey,
        triggerSource = ActivityTaskTriggerSource.valueOf(entity.triggerSource),
        triggerTime = entity.triggerTime,
        status = ActivityTaskExecutionStatus.valueOf(entity.status),
        attemptNo = entity.attemptNo,
        result = entity.resultJson?.let(::readMap),
        errorMessage = entity.errorMessage,
        startTime = entity.startTime,
        endTime = entity.endTime
    )

    private companion object {
        /** 分布式任务租约时长，单位为毫秒。 */
        const val LEASE_MILLIS = 60_000L

        /** 可复用编码格式。 */
        val CODE_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")

        /** JSON 对象读取类型。 */
        val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
