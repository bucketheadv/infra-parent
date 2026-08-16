package io.infra.structure.rocketmq.admin.service

import io.infra.structure.rocketmq.admin.dto.RocketMQMessageListItem
import io.infra.structure.rocketmq.admin.dto.RocketMQMessageView
import io.infra.structure.rocketmq.admin.dto.RocketMQResendResult
import io.infra.structure.rocketmq.admin.dto.RocketMQSendResult
import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import io.infra.structure.rocketmq.admin.support.RocketMQAdminClient
import io.infra.structure.rocketmq.admin.web.RocketMQAdminException
import org.apache.rocketmq.acl.common.AclClientRPCHook
import org.apache.rocketmq.acl.common.SessionCredentials
import org.apache.rocketmq.client.exception.MQBrokerException
import org.apache.rocketmq.client.exception.MQClientException
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.common.MixAll
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.common.message.MessageConst
import org.apache.rocketmq.common.message.MessageExt
import org.apache.rocketmq.remoting.RPCHook
import org.apache.rocketmq.remoting.protocol.ResponseCode
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** 消息查询、详情与重发。 */
@Service
class RocketMQMessageService(
    private val client: RocketMQAdminClient,
    private val properties: RocketMQAdminProperties
) {

    /** 默认回查时间窗口：最近 3 天。 */
    private val defaultQueryWindowMillis = 3L * 24 * 60 * 60 * 1000

    /** 按 Topic + 时间范围或 Key 查询消息索引。 */
    fun queryMessages(
        topic: String,
        key: String?,
        beginTime: Long?,
        endTime: Long?,
        maxNum: Int
    ): List<RocketMQMessageListItem> {
        val topicName = topic.trim()
        require(topicName.isNotBlank()) { "Topic 不能为空" }
        val end = endTime ?: System.currentTimeMillis()
        val begin = beginTime ?: (end - defaultQueryWindowMillis)
        require(begin <= end) { "起始时间不能晚于结束时间" }
        return client.execute { admin ->
            val limit = maxNum.coerceIn(1, 500)
            val messages = if (key.isNullOrBlank()) {
                queryRecentMessages(topicName, begin, end, limit)
            } else {
                val result = try {
                    admin.queryMessage(topicName, key, limit, begin, end)
                } catch (exception: MQBrokerException) {
                    if (exception.responseCode == ResponseCode.QUERY_NOT_FOUND) {
                        return@execute emptyList()
                    }
                    throw translate("消息查询失败", exception)
                } catch (exception: MQClientException) {
                    if (exception.responseCode == ResponseCode.QUERY_NOT_FOUND ||
                        exception.responseCode == ResponseCode.NO_MESSAGE
                    ) {
                        return@execute emptyList()
                    }
                    throw translate("消息查询失败", exception)
                } catch (exception: Exception) {
                    throw translate("消息查询失败", exception)
                }
                result.messageList ?: emptyList()
            }
            messages.sortedByDescending { it.storeTimestamp }
                .take(limit)
                .map(::toListItem)
        }
    }

    /** 按消息 ID 加载完整消息详情；优先按 offsetMsgId，失败后回退到唯一 ID + Topic。 */
    fun viewMessage(msgId: String, topic: String?): RocketMQMessageView {
        val message = loadMessage(msgId.trim(), topic?.trim())
            ?: throw RocketMQAdminException("消息不存在或已过期：$msgId")
        return toMessageView(message)
    }

    /** 重发死信消息到目标 Topic（默认原 Topic），可按需携带延迟等级。 */
    fun resendMessage(msgId: String, topic: String?, targetTopic: String?, delayLevel: Int): RocketMQResendResult {
        val message = loadMessage(msgId.trim(), topic?.trim())
            ?: throw RocketMQAdminException("消息不存在或已过期：$msgId")
        val originTopic = message.getProperty(MessageConst.PROPERTY_DLQ_ORIGIN_TOPIC)
            ?: message.getProperty("ORIGIN_TOPIC")
            ?: message.topic
        val target = targetTopic?.takeIf { it.isNotBlank() } ?: originTopic
        withProducer { producer ->
            val resend = Message().apply {
                this.topic = target
                this.body = message.body ?: ByteArray(0)
                this.tags = message.tags
                this.keys = message.keys
                setDelayTimeLevel(delayLevel.coerceIn(0, 100))
            }
            try {
                producer.send(resend)
            } catch (exception: Exception) {
                throw translate("消息重发失败：$msgId -> $target", exception)
            }
        }
        return RocketMQResendResult(
            msgId = message.msgId,
            originalTopic = message.topic,
            targetTopic = target,
            delayLevel = delayLevel
        )
    }

    /** 发送一条测试消息到指定 Topic。 */
    fun sendMessage(topic: String, body: String, tags: String?, keys: String?, delayLevel: Int): RocketMQSendResult {
        val topicName = topic.trim()
        require(topicName.isNotBlank()) { "Topic 不能为空" }
        require(body.isNotBlank()) { "消息内容不能为空" }
        var sendResult: org.apache.rocketmq.client.producer.SendResult? = null
        withProducer { producer ->
            val message = Message().apply {
                this.topic = topicName
                this.body = body.toByteArray(StandardCharsets.UTF_8)
                if (!tags.isNullOrBlank()) this.tags = tags
                if (!keys.isNullOrBlank()) this.keys = keys
                setDelayTimeLevel(delayLevel.coerceIn(0, 100))
            }
            try {
                sendResult = producer.send(message)
            } catch (exception: Exception) {
                throw translate("消息发送失败：$topicName", exception)
            }
        }
        return RocketMQSendResult(
            msgId = sendResult?.msgId ?: "-",
            offsetMsgId = sendResult?.offsetMsgId ?: "-",
            topic = topicName
        )
    }

    /** 按消息 ID 加载消息；offsetMsgId 与唯一 ID 均可，但必须提供 Topic 才能精确定位。 */
    private fun loadMessage(msgId: String, topic: String?): MessageExt? {
        if (msgId.isBlank() || topic.isNullOrBlank()) return null
        return client.execute { admin ->
            try {
                admin.queryMessage(null, topic, msgId)
            } catch (_: Exception) {
                null
            }
        }
    }

    /** 未指定 Key 时，从各队列末尾读取指定时间范围内的最近消息。 */
    private fun queryRecentMessages(topic: String, begin: Long, end: Long, limit: Int): List<MessageExt> {
        val queues = try {
            withPullConsumer { it.fetchMessageQueues(topic) }
        } catch (exception: Exception) {
            throw translate("无法获取 Topic 队列：$topic", exception)
        }
        if (queues.isEmpty()) return emptyList()
        // 每个队列独立 assign + seek + poll：各队列并行、专注单队列，取回最近 limit 条
        return queues.flatMap { queue ->
            try {
                withPullConsumer { consumer ->
                    consumer.assign(listOf(queue))
                    val seekOffset = (consumer.offsetForTimestamp(queue, end) ?: 0L) - limit
                    consumer.seek(queue, seekOffset.coerceAtLeast(0L))
                    val messages = ArrayList<MessageExt>()
                    val deadline = System.currentTimeMillis() + properties.operationTimeoutMillis
                    while (messages.size < limit && System.currentTimeMillis() < deadline) {
                        val batch = consumer.poll(200L)
                        if (batch.isEmpty()) break
                        messages += batch.filter { it.storeTimestamp in begin..end }
                    }
                    messages.take(limit)
                }
            } catch (exception: Exception) {
                throw translate("读取 Topic 队列消息失败：${queue.brokerName}/${queue.queueId}", exception)
            }
        }
    }

    /** 创建一次性 Lite Pull Consumer，仅用于无 Key 的管理端消息浏览。 */
    private fun <T> withPullConsumer(block: (DefaultLitePullConsumer) -> T): T {
        val rpcHook: RPCHook? = properties.accessKey?.takeIf { it.isNotBlank() }?.let {
            AclClientRPCHook(SessionCredentials(it, properties.secretKey ?: ""))
        }
        val group = "infra_rocketmq_admin_query_${UUID.randomUUID()}"
        val consumer = if (rpcHook == null) DefaultLitePullConsumer(group) else DefaultLitePullConsumer(group, rpcHook)
        consumer.namesrvAddr = properties.namesrvAddr
        consumer.instanceName = "$group-${properties.instanceName}"
        consumer.isUseTLS = properties.useTLS
        consumer.isVipChannelEnabled = false
        consumer.consumerPullTimeoutMillis = properties.operationTimeoutMillis
        return try {
            consumer.start()
            block(consumer)
        } catch (exception: RocketMQAdminException) {
            throw exception
        } catch (exception: Exception) {
            throw translate("初始化消息读取客户端失败", exception)
        } finally {
            consumer.shutdown()
        }
    }

    private fun toListItem(message: MessageExt): RocketMQMessageListItem = RocketMQMessageListItem(
        msgId = message.msgId,
        topic = message.topic,
        tags = message.tags,
        keys = message.keys,
        bornHost = message.bornHostString,
        storeHost = message.storeHost?.toString() ?: "-",
        queueId = message.queueId,
        queueOffset = message.queueOffset,
        bornTimestamp = message.bornTimestamp,
        storeTimestamp = message.storeTimestamp,
        reconsumeTimes = message.reconsumeTimes,
        deadLetter = message.topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)
    )

    private fun toMessageView(message: MessageExt): RocketMQMessageView {
        val (bodyText, bodyHex) = decodeBody(message.body)
        return RocketMQMessageView(
            msgId = message.msgId,
            topic = message.topic,
            tags = message.tags,
            keys = message.keys,
            bornHost = message.bornHostString,
            storeHost = message.storeHost?.toString() ?: "-",
            queueId = message.queueId,
            queueOffset = message.queueOffset,
            bornTimestamp = message.bornTimestamp,
            storeTimestamp = message.storeTimestamp,
            reconsumeTimes = message.reconsumeTimes,
            delayLevel = message.delayTimeLevel,
            bodySize = message.body?.size ?: 0,
            bodyText = bodyText,
            bodyHex = bodyHex,
            properties = message.properties?.toSortedMap() ?: emptyMap(),
            deadLetter = message.topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)
        )
    }

    /** 正文解码：优先文本，尝试 gzip/deflate 解压，最终回退十六进制。 */
    private fun decodeBody(body: ByteArray?): Pair<String?, String> {
        if (body == null || body.isEmpty()) return null to ""
        val hex = body.joinToString("") { "%02x".format(it) }
        val utf8 = runCatching { String(body, StandardCharsets.UTF_8) }.getOrNull()
        if (utf8 != null && isReadable(utf8)) return utf8 to hex
        val inflated = tryInflate(body)
        if (inflated != null) {
            val text = runCatching { String(inflated, StandardCharsets.UTF_8) }.getOrNull()
            if (text != null && isReadable(text)) return text to hex
        }
        return null to hex
    }

    private fun isReadable(text: String): Boolean {
        if (text.isEmpty()) return false
        val limit = minOf(text.length, 1_000)
        var printable = 0
        for (index in 0 until limit) {
            val code = text[index].code
            if (code == '\n'.code || code == '\r'.code || code == '\t'.code || code == ' '.code ||
                (code in 0x20..0x7E) || code >= 0x80
            ) {
                printable++
            }
        }
        return printable.toDouble() / limit >= 0.9
    }

    private fun tryInflate(body: ByteArray): ByteArray? {
        val compressed = body.size >= 2 && (body[0].toInt() and 0xFF) == 0x1F && (body[1].toInt() and 0xFF) == 0x8B ||
            body.size >= 2 && (body[0].toInt() and 0xFF) == 0x78
        if (!compressed) return null
        return runCatching {
            val stream = if ((body[0].toInt() and 0xFF) == 0x1F) {
                GZIPInputStream(ByteArrayInputStream(body))
            } else {
                InflaterInputStream(ByteArrayInputStream(body))
            }
            stream.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 使用独立 Producer 执行发送类操作，避免复用管理客户端的连接。 */
    private fun <T> withProducer(block: (DefaultMQProducer) -> T): T {
        val rpcHook: RPCHook? = properties.accessKey?.takeIf { it.isNotBlank() }?.let {
            AclClientRPCHook(SessionCredentials(it, properties.secretKey ?: ""))
        }
        val producer = if (rpcHook != null) {
            DefaultMQProducer(PRODUCER_GROUP, rpcHook)
        } else {
            DefaultMQProducer(PRODUCER_GROUP)
        }
        producer.namesrvAddr = properties.namesrvAddr
        producer.instanceName = properties.instanceName
        producer.isUseTLS = properties.useTLS
        producer.isVipChannelEnabled = false
        producer.sendMsgTimeout = properties.operationTimeoutMillis.toInt().coerceIn(1_000, 60_000)
        try {
            producer.start()
        } catch (exception: Exception) {
            throw RocketMQAdminException("无法连接 RocketMQ NameServer（${properties.namesrvAddr}）：${exception.message}", exception)
        }
        try {
            return block(producer)
        } finally {
            runCatching { producer.shutdown() }
        }
    }

    private fun translate(action: String, exception: Exception): RocketMQAdminException =
        RocketMQAdminException("$action：${exception.message}", exception)

    private companion object {
        /** 管理后台发送/重发消息使用的临时 Producer 组。 */
        const val PRODUCER_GROUP = "infra-rocketmq-admin"
    }
}
