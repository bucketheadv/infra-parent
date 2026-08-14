package io.infra.structure.rocketmq.admin.web

import com.fasterxml.jackson.annotation.JsonProperty
import io.infra.structure.rocketmq.admin.dto.RocketMQResendResult
import io.infra.structure.rocketmq.admin.dto.RocketMQSendResult
import io.infra.structure.rocketmq.admin.service.RocketMQClusterService
import io.infra.structure.rocketmq.admin.service.RocketMQConsumerGroupService
import io.infra.structure.rocketmq.admin.service.RocketMQMessageService
import io.infra.structure.rocketmq.admin.service.RocketMQTopicService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** RocketMQ 管理 REST 接口。 */
@RestController
@RequestMapping(RocketMQAdminWebPaths.API_ROOT)
class RocketMQAdminApiController(
    private val clusterService: RocketMQClusterService,
    private val topicService: RocketMQTopicService,
    private val consumerGroupService: RocketMQConsumerGroupService,
    private val messageService: RocketMQMessageService
) {

    @GetMapping(RocketMQAdminWebPaths.API_CLUSTER)
    fun cluster() = clusterService.summary()

    @GetMapping(RocketMQAdminWebPaths.API_TOPICS)
    fun topics() = topicService.topics()

    @GetMapping(RocketMQAdminWebPaths.API_TOPIC_BY_NAME)
    fun topic(@PathVariable topic: String) = topicService.topicDetail(topic)

    @PostMapping(RocketMQAdminWebPaths.API_TOPICS)
    fun createTopic(@Valid @RequestBody request: CreateTopicRequest): ResponseEntity<Unit> {
        topicService.createTopic(
            request.name,
            requireNotNull(request.readQueueNums) { "读队列数不能为空" },
            requireNotNull(request.writeQueueNums) { "写队列数不能为空" }
        )
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping(RocketMQAdminWebPaths.API_TOPIC_BY_NAME)
    fun deleteTopic(@PathVariable topic: String): ResponseEntity<Unit> {
        topicService.deleteTopic(topic)
        return ResponseEntity.noContent().build()
    }

    @GetMapping(RocketMQAdminWebPaths.API_CONSUMER_GROUPS)
    fun consumerGroups() = consumerGroupService.consumerGroups()

    @GetMapping(RocketMQAdminWebPaths.API_CONSUMER_GROUP_BY_NAME)
    fun consumerGroup(@PathVariable group: String) = consumerGroupService.consumerGroupDetail(group)

    @PostMapping(RocketMQAdminWebPaths.API_CONSUMER_GROUP_RESET)
    fun resetConsumerGroup(
        @PathVariable group: String,
        @Valid @RequestBody request: ResetOffsetRequest
    ) = consumerGroupService.resetOffsetByTimestamp(group, request.topic, request.timestamp, request.force)

    @GetMapping(RocketMQAdminWebPaths.API_MESSAGE_QUERY)
    fun queryMessages(
        @RequestParam @NotBlank topic: String,
        @RequestParam(required = false) key: String?,
        @RequestParam(required = false) begin: Long?,
        @RequestParam(required = false) end: Long?,
        @RequestParam(defaultValue = "100") @Min(1) @Max(500) maxNum: Int
    ) = messageService.queryMessages(topic, key, begin, end, maxNum)

    @GetMapping(RocketMQAdminWebPaths.API_MESSAGE_DETAIL)
    fun messageDetail(
        @PathVariable msgId: String,
        @RequestParam(required = false) topic: String?
    ) = messageService.viewMessage(msgId, topic)

    @PostMapping(RocketMQAdminWebPaths.API_MESSAGE_RESEND)
    fun resendMessage(
        @PathVariable msgId: String,
        @Valid @RequestBody request: ResendMessageRequest
    ): RocketMQResendResult = messageService.resendMessage(msgId, request.topic, request.targetTopic, request.delayLevel)

    @PostMapping(RocketMQAdminWebPaths.API_MESSAGE_SEND)
    fun sendMessage(@Valid @RequestBody request: SendMessageRequest): RocketMQSendResult =
        messageService.sendMessage(request.topic, request.body, request.tags, request.keys, request.delayLevel)
}

/** 创建 Topic 的请求载荷。 */
data class CreateTopicRequest(
    /** Topic 名称。 */
    @param:JsonProperty("name")
    @field:NotBlank val name: String,
    /** 读队列数量。 */
    @param:JsonProperty("read_queue_nums")
    @field:NotNull(message = "读队列数不能为空")
    @field:Min(1) @field:Max(1024) val readQueueNums: Int? = 4,
    /** 写队列数量。 */
    @param:JsonProperty("write_queue_nums")
    @field:NotNull(message = "写队列数不能为空")
    @field:Min(1) @field:Max(1024) val writeQueueNums: Int? = 4
)

/** 重置消费位点的请求载荷。 */
data class ResetOffsetRequest(
    /** 目标 Topic；为空时重置该消费组的所有 Topic。 */
    val topic: String? = null,
    /** 位点重置的目标 Unix 毫秒时间戳。 */
    @field:Min(0) val timestamp: Long,
    /** 是否强制重置（会跳过是否更晚于当前位点的检查）。 */
    val force: Boolean = false
)

/** 重发消息的请求载荷。 */
data class ResendMessageRequest(
    /** 消息所属 Topic，用于定位消息。 */
    val topic: String? = null,
    /** 目标 Topic；为空时回投到原 Topic。 */
    val targetTopic: String? = null,
    /** 延迟等级；0 表示立即投递。 */
    @field:Min(0) @field:Max(100) val delayLevel: Int = 0
)

/** 发送测试消息的请求载荷。 */
data class SendMessageRequest(
    @field:NotBlank val topic: String,
    @field:NotBlank val body: String,
    val tags: String? = null,
    val keys: String? = null,
    @field:Min(0) @field:Max(100) val delayLevel: Int = 0
)
