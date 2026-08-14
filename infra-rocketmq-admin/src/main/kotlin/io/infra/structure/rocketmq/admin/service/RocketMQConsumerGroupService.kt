package io.infra.structure.rocketmq.admin.service

import io.infra.structure.rocketmq.admin.dto.RocketMQConsumeProgressView
import io.infra.structure.rocketmq.admin.dto.RocketMQConsumerConnectionView
import io.infra.structure.rocketmq.admin.dto.RocketMQConsumerGroupDetail
import io.infra.structure.rocketmq.admin.dto.RocketMQConsumerGroupView
import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import io.infra.structure.rocketmq.admin.support.RocketMQAdminClient
import io.infra.structure.rocketmq.admin.web.RocketMQAdminException
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats
import org.apache.rocketmq.remoting.protocol.body.Connection
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection
import org.springframework.stereotype.Service

/** 消费组查询与位点维护。 */
@Service
class RocketMQConsumerGroupService(
    private val client: RocketMQAdminClient,
    private val properties: RocketMQAdminProperties
) {

    /** 全量消费组列表，附带消费 TPS、堆积量与在线状态。 */
    fun consumerGroups(): List<RocketMQConsumerGroupView> {
        val groups = allConsumerGroups().sorted()
        val now = System.currentTimeMillis()
        return groups.mapNotNull { group ->
            val stats = consumeStats(group) ?: return@mapNotNull null
            val online = isOnline(group)
            RocketMQConsumerGroupView(
                group = group,
                consumeTps = stats.consumeTps,
                diffTotal = stats.computeTotalDiff(),
                online = online,
                lastTimestamp = stats.offsetTable.values
                    .maxOfOrNull { it.lastTimestamp }
                    ?: 0L,
                topics = stats.offsetTable.keys.map { it.topic }.distinct().sorted()
            )
        }
    }

    /** 单个消费组详情：在线连接、订阅 Topic 与各队列消费进度。 */
    fun consumerGroupDetail(group: String): RocketMQConsumerGroupDetail {
        val connection = consumerConnection(group)
        val stats = consumeStats(group)
            ?: throw RocketMQAdminException("消费组不存在或尚未开始消费：$group")
        val offsetTable = stats.offsetTable
        return RocketMQConsumerGroupDetail(
            group = group,
            online = connection != null,
            connection = connection,
            topics = offsetTable.keys.map { it.topic }.distinct().sorted(),
            progress = offsetTable
                .map { (queue, wrapper) ->
                    RocketMQConsumeProgressView(
                        topic = queue.topic,
                        brokerName = queue.brokerName,
                        queueId = queue.queueId,
                        brokerOffset = wrapper.brokerOffset,
                        consumerOffset = wrapper.consumerOffset,
                        diff = (wrapper.brokerOffset - wrapper.consumerOffset).coerceAtLeast(0),
                        lastTimestamp = wrapper.lastTimestamp,
                        clientId = "-"
                    )
                }
                .sortedWith(compareBy({ it.brokerName }, { it.queueId }))
        )
    }

    /** 按时间戳重置消费位点；topic 为空时重置该消费组的所有 Topic。 */
    fun resetOffsetByTimestamp(group: String, topic: String?, timestamp: Long, force: Boolean): List<RocketMQConsumeProgressView> =
        client.execute { admin ->
            val reset = try {
                admin.resetOffsetByTimestamp(group, topic ?: "", timestamp, force)
            } catch (exception: Exception) {
                throw translate("重置消费位点失败：$group", exception)
            }
            reset
                .map { (queue, offset) ->
                    RocketMQConsumeProgressView(
                        topic = queue.topic,
                        brokerName = queue.brokerName,
                        queueId = queue.queueId,
                        brokerOffset = offset,
                        consumerOffset = 0L,
                        diff = 0L,
                        lastTimestamp = timestamp,
                        clientId = "-"
                    )
                }
                .sortedWith(compareBy({ it.brokerName }, { it.queueId }))
        }

    /** 消费组列表：汇总各主 Broker 上注册的订阅组。 */
    private fun allConsumerGroups(): Set<String> = client.execute { admin ->
        val groups = LinkedHashSet<String>()
        admin.examineBrokerClusterInfo().brokerAddrTable.forEach { (_, brokerData) ->
            brokerData.brokerAddrs[0L]?.let { addr ->
                try {
                    val table = admin.getAllSubscriptionGroup(addr, properties.operationTimeoutMillis).subscriptionGroupTable
                    table?.keys?.let { groups.addAll(it) }
                } catch (exception: Exception) {
                    // 单个 Broker 不可用时跳过
                }
            }
        }
        groups
    }

    private fun consumeStats(group: String): ConsumeStats? = try {
        client.execute { it.examineConsumeStats(group) }
    } catch (exception: Exception) {
        null
    }

    private fun consumerConnection(group: String): RocketMQConsumerConnectionView? = try {
        client.execute { admin ->
            val connection: ConsumerConnection = admin.examineConsumerConnectionInfo(group)
            val clients = connection.connectionSet.sortedBy { it.clientId }
            if (clients.isEmpty()) {
                null
            } else {
                val representative = clients.first()
                RocketMQConsumerConnectionView(
                    version = formatVersion(representative.version),
                    language = representative.language?.let { it.name } ?: "-",
                    clientId = clients.joinToString(", ") { it.clientId }
                )
            }
        }
    } catch (exception: Exception) {
        null
    }

    private fun isOnline(group: String): Boolean = consumerConnection(group) != null

    private fun formatVersion(version: Int): String {
        if (version <= 0) return "-"
        val major = version ushr 24 and 0xFF
        val minor = version ushr 16 and 0xFF
        val patch = version ushr 8 and 0xFF
        return "$major.$minor.$patch"
    }

    private fun translate(action: String, exception: Exception): RocketMQAdminException =
        RocketMQAdminException("$action：${exception.message}", exception)
}