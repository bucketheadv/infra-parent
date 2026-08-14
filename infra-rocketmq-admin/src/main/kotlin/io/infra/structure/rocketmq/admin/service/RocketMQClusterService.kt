package io.infra.structure.rocketmq.admin.service

import io.infra.structure.rocketmq.admin.dto.RocketMQBrokerView
import io.infra.structure.rocketmq.admin.dto.RocketMQClusterSummary
import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import io.infra.structure.rocketmq.admin.support.RocketMQAdminClient
import io.infra.structure.rocketmq.admin.web.RocketMQAdminException
import org.apache.rocketmq.common.MQVersion
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 集群与 Broker 概览查询。 */
@Service
class RocketMQClusterService(
    private val client: RocketMQAdminClient,
    private val properties: RocketMQAdminProperties
) {

    private val log = LoggerFactory.getLogger(RocketMQClusterService::class.java)

    /** 集群概览：Broker 列表与 Topic/消费组数量。 */
    fun summary(): RocketMQClusterSummary = client.execute { admin ->
        val clusterInfo = try {
            admin.examineBrokerClusterInfo()
        } catch (exception: Exception) {
            throw translate("无法获取集群信息", exception)
        }
        val topics = try {
            admin.fetchAllTopicList().topicList
        } catch (exception: Exception) {
            throw translate("无法获取 Topic 列表", exception)
        }
        RocketMQClusterSummary(
            nameServerAddr = properties.namesrvAddr,
            brokers = toBrokerViews(admin, clusterInfo),
            topicCount = topics?.size ?: 0,
            consumerGroupCount = consumerGroupCount(admin)
        )
    }

    /** Broker 地址表：brokerName -> 主（master）地址。 */
    fun masterBrokerAddrs(): List<String> = client.execute { admin ->
        admin.examineBrokerClusterInfo().brokerAddrTable.values
            .mapNotNull { brokerData -> brokerData.brokerAddrs[0L] }
            .distinct()
    }

    private fun consumerGroupCount(admin: org.apache.rocketmq.tools.admin.DefaultMQAdminExt): Int {
        val cluster = try {
            admin.examineBrokerClusterInfo()
        } catch (exception: Exception) {
            return 0
        }
        val groups = LinkedHashSet<String>()
        cluster.brokerAddrTable.forEach { (_, brokerData) ->
            brokerData.brokerAddrs[0L]?.let { addr ->
                try {
                    val table = admin.getAllSubscriptionGroup(addr, properties.operationTimeoutMillis).subscriptionGroupTable
                    table?.keys?.let { groups.addAll(it) }
                } catch (exception: Exception) {
                    // 单个 Broker 不可用时跳过，避免整个概览失败
                }
            }
        }
        return groups.size
    }

    private fun toBrokerViews(
        admin: DefaultMQAdminExt,
        clusterInfo: ClusterInfo
    ): List<RocketMQBrokerView> {
        val views = ArrayList<RocketMQBrokerView>()
        clusterInfo.brokerAddrTable.forEach { (brokerName, brokerData) ->
            val clusterName = clusterInfo.clusterAddrTable
                ?.entries
                ?.firstOrNull { (_, brokers) -> brokerName in brokers }
                ?.key
                ?: brokerData.cluster
                ?: "-"
            brokerData.brokerAddrs.forEach { (brokerId, addr) ->
                views += RocketMQBrokerView(
                    clusterName = clusterName,
                    brokerName = brokerName,
                    brokerId = brokerId,
                    address = addr,
                    version = brokerVersion(admin, addr),
                    inTotalYest = 0L,
                    outTotalYest = 0L
                )
            }
        }
        return views.sortedWith(compareBy({ it.clusterName }, { it.brokerName }, { it.brokerId }))
    }

    private fun brokerVersion(admin: DefaultMQAdminExt, address: String): String {
        return try {
            admin.fetchBrokerRuntimeStats(address).table?.get("brokerVersion")
                ?.let(::formatBrokerVersion)
                ?: "-"
        } catch (exception: Exception) {
            log.warn("无法获取 Broker 运行时版本，地址: {}，原因: {}", address, exception.message)
            "-"
        }
    }

    /** 将 Broker 上报的内部协议版本号转换为 RocketMQ 发布版本。 */
    private fun formatBrokerVersion(version: String): String {
        val code = version.toIntOrNull() ?: return version
        val release = runCatching { MQVersion.getVersionDesc(code) }.getOrNull() ?: return version
        return "${release.removePrefix("V").replace('_', '.')}（协议版本 $code）"
    }

    private fun translate(action: String, exception: Exception): RocketMQAdminException {
        return RocketMQAdminException("$action：${exception.message}", exception)
    }
}
