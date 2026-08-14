package io.infra.structure.rocketmq.admin.service

import io.infra.structure.rocketmq.admin.dto.RocketMQQueueOffsetView
import io.infra.structure.rocketmq.admin.dto.RocketMQTopicDetail
import io.infra.structure.rocketmq.admin.dto.RocketMQTopicView
import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import io.infra.structure.rocketmq.admin.support.RocketMQAdminClient
import io.infra.structure.rocketmq.admin.web.RocketMQAdminException
import org.apache.rocketmq.common.TopicConfig
import org.springframework.stereotype.Service

/** Topic 查询与维护。 */
@Service
class RocketMQTopicService(
    private val client: RocketMQAdminClient,
    private val clusterService: RocketMQClusterService,
    private val properties: RocketMQAdminProperties
) {

    /** 全量 Topic 列表，附带各 Topic 的队列配置。 */
    fun topics(): List<RocketMQTopicView> = client.execute { admin ->
        val allTopics = try {
            admin.fetchAllTopicList().topicList ?: emptySet()
        } catch (exception: Exception) {
            throw translate("无法获取 Topic 列表", exception)
        }
        val configs = topicConfigs(admin)
        allTopics
            .filterNot { it.startsWith(SYSTEM_TOPIC_PREFIX) }
            .map { topic ->
                val config = configs[topic]
                RocketMQTopicView(
                    topic = topic,
                    readQueueNums = config?.readQueueNums ?: 0,
                    writeQueueNums = config?.writeQueueNums ?: 0,
                    perm = config?.perm ?: 0,
                    order = config?.isOrder ?: false,
                    messageCount = messageCount(admin, topic)
                )
            }
            .sortedBy { it.topic }
    }

    /** 单个 Topic 详情：路由 Broker、队列位点、生产与消费关系。 */
    fun topicDetail(topic: String): RocketMQTopicDetail = client.execute { admin ->
        val route = try {
            admin.examineTopicRouteInfo(topic)
        } catch (exception: Exception) {
            throw RocketMQAdminException("Topic 不存在或路由获取失败：$topic", exception)
        }
        val stats = try {
            admin.examineTopicStats(topic)
        } catch (exception: Exception) {
            throw translate("无法获取 Topic 位点：$topic", exception)
        }
        val config = topicConfigs(admin)[topic]
        RocketMQTopicDetail(
            topic = topic,
            readQueueNums = config?.readQueueNums ?: 0,
            writeQueueNums = config?.writeQueueNums ?: 0,
            perm = config?.perm ?: 0,
            brokers = route.brokerDatas.map { it.brokerName }.distinct().sorted(),
            queueOffsets = stats.offsetTable
                .map { (queue, offset) ->
                    RocketMQQueueOffsetView(
                        brokerName = queue.brokerName,
                        queueId = queue.queueId,
                        minOffset = offset.minOffset,
                        maxOffset = offset.maxOffset,
                        lastUpdateTimestamp = offset.lastUpdateTimestamp
                    )
                }
                .sortedWith(compareBy({ it.brokerName }, { it.queueId })),
            producerGroups = producerGroups(admin, topic),
            consumerGroups = consumerGroups(admin, topic)
        )
    }

    /** 在全部主 Broker 上创建 Topic。 */
    fun createTopic(topic: String, readQueueNums: Int, writeQueueNums: Int): Unit = client.execute { admin ->
        val topicName = topic.trim()
        require(topicName.isNotBlank()) { "Topic 名称不能为空" }
        require(topicName.length <= 127) { "Topic 名称长度不能超过 127 个字符" }
        val brokers = clusterService.masterBrokerAddrs()
        if (brokers.isEmpty()) {
            throw RocketMQAdminException("集群中不存在可用主 Broker，无法创建 Topic")
        }
        val config = TopicConfig().apply {
            this.topicName = topicName
            this.readQueueNums = readQueueNums.coerceIn(1, 1024)
            this.writeQueueNums = writeQueueNums.coerceIn(1, 1024)
            this.perm = 6
        }
        brokers.forEach { addr ->
            try {
                admin.createAndUpdateTopicConfig(addr, config)
            } catch (exception: Exception) {
                throw translate("在 Broker $addr 上创建 Topic 失败：$topicName", exception)
            }
        }
    }

    /** 删除 Broker 与 NameServer 上的 Topic 路由。 */
    fun deleteTopic(topic: String): Unit = client.execute { admin ->
        val topicName = topic.trim()
        require(topicName.isNotBlank()) { "Topic 名称不能为空" }
        val brokers = clusterService.masterBrokerAddrs()
        brokers.forEach { addr ->
            try {
                admin.deleteTopicInBroker(setOf(addr), topicName)
            } catch (exception: Exception) {
                throw translate("删除 Broker $addr 上的 Topic 失败：$topicName", exception)
            }
        }
        val nameServerAddrs = admin.nameServerAddressList.toSet()
        if (nameServerAddrs.isEmpty()) {
            throw RocketMQAdminException("未获取到可用 NameServer，无法删除 Topic 路由：$topicName")
        }
        try {
            admin.deleteTopicInNameServer(nameServerAddrs, topicName)
        } catch (exception: Exception) {
            throw translate("删除 NameServer 上的 Topic 路由失败：$topicName", exception)
        }
        val topicStillExists = try {
            admin.fetchAllTopicList().topicList?.contains(topicName) == true
        } catch (exception: Exception) {
            throw translate("校验 Topic 删除结果失败：$topicName", exception)
        }
        if (topicStillExists) {
            throw RocketMQAdminException("Topic 路由仍存在，未完成删除：$topicName")
        }
    }

    /** 汇总所有主 Broker 上的 Topic 配置，供列表页一次性使用。 */
    private fun topicConfigs(admin: org.apache.rocketmq.tools.admin.DefaultMQAdminExt): Map<String, TopicConfig> {
        val result = LinkedHashMap<String, TopicConfig>()
        clusterService.masterBrokerAddrs().forEach { addr ->
            try {
                val table = admin.getAllTopicConfig(addr, properties.operationTimeoutMillis).topicConfigTable
                if (table != null) result.putAll(table)
            } catch (exception: Exception) {
                // 单个 Broker 不可用时跳过
            }
        }
        return result
    }

    /** 汇总 Topic 各队列当前保留的消息数量。 */
    private fun messageCount(admin: org.apache.rocketmq.tools.admin.DefaultMQAdminExt, topic: String): Long =
        try {
            admin.examineTopicStats(topic).offsetTable.values
                .sumOf { offset -> (offset.maxOffset - offset.minOffset).coerceAtLeast(0) }
        } catch (exception: Exception) {
            0L
        }

    private fun producerGroups(admin: org.apache.rocketmq.tools.admin.DefaultMQAdminExt, topic: String): List<String> =
        try {
            admin.getAllProducerInfo(topic).data?.keys?.sorted() ?: emptyList()
        } catch (exception: Exception) {
            emptyList()
        }

    private fun consumerGroups(admin: org.apache.rocketmq.tools.admin.DefaultMQAdminExt, topic: String): List<String> =
        try {
            admin.queryTopicConsumeByWho(topic).groupList?.sorted() ?: emptyList()
        } catch (exception: Exception) {
            emptyList()
        }

    private fun translate(action: String, exception: Exception): RocketMQAdminException =
        RocketMQAdminException("$action：${exception.message}", exception)

    private companion object {
        /** RocketMQ 内置 Topic 前缀（系统主题与重试/死信主题），列表页默认隐藏。 */
        const val SYSTEM_TOPIC_PREFIX = "%"
    }
}
