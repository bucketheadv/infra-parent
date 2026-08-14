package io.infra.structure.rocketmq.admin.dto

/** 集群概览数据，用于仪表盘展示。 */
data class RocketMQClusterSummary(
    /** 当前管理的 NameServer 地址。 */
    val nameServerAddr: String,
    /** Broker 列表。 */
    val brokers: List<RocketMQBrokerView>,
    /** 全量 Topic 数量。 */
    val topicCount: Int,
    /** 全量消费组数量。 */
    val consumerGroupCount: Int
)

/** Broker 基本信息。 */
data class RocketMQBrokerView(
    /** Broker 所属集群名称。 */
    val clusterName: String,
    /** Broker 名称。 */
    val brokerName: String,
    /** Broker 节点 ID；0 表示主节点。 */
    val brokerId: Long,
    /** Broker 服务地址。 */
    val address: String,
    /** Broker 运行版本。 */
    val version: String,
    /** 昨日流入消息总数。 */
    val inTotalYest: Long,
    /** 昨日流出消息总数。 */
    val outTotalYest: Long
)

/** Topic 列表项。 */
data class RocketMQTopicView(
    /** Topic 名称。 */
    val topic: String,
    /** 读队列数量。 */
    val readQueueNums: Int,
    /** 写队列数量。 */
    val writeQueueNums: Int,
    /** Topic 权限位。 */
    val perm: Int,
    /** 是否为顺序 Topic。 */
    val order: Boolean,
    /** Topic 当前保留的消息数量。 */
    val messageCount: Long
)

/** Topic 详情：路由、队列位点与生产/消费关系。 */
data class RocketMQTopicDetail(
    /** Topic 名称。 */
    val topic: String,
    /** 读队列数量。 */
    val readQueueNums: Int,
    /** 写队列数量。 */
    val writeQueueNums: Int,
    /** Topic 权限位。 */
    val perm: Int,
    /** 承载该 Topic 的 Broker 名称列表。 */
    val brokers: List<String>,
    /** 各消息队列的位点信息。 */
    val queueOffsets: List<RocketMQQueueOffsetView>,
    /** 生产该 Topic 的生产者组列表。 */
    val producerGroups: List<String>,
    /** 消费该 Topic 的消费者组列表。 */
    val consumerGroups: List<String>
)

/** 单个队列的位点信息。 */
data class RocketMQQueueOffsetView(
    /** Broker 名称。 */
    val brokerName: String,
    /** 队列 ID。 */
    val queueId: Int,
    /** 最小消息位点。 */
    val minOffset: Long,
    /** 最大消息位点。 */
    val maxOffset: Long,
    /** 位点最后更新时间戳（毫秒）。 */
    val lastUpdateTimestamp: Long
)

/** 消费组列表项。 */
data class RocketMQConsumerGroupView(
    /** 消费者组名称。 */
    val group: String,
    /** 消费 TPS。 */
    val consumeTps: Double,
    /** 消费积压消息总数。 */
    val diffTotal: Long,
    /** 消费者组是否在线。 */
    val online: Boolean,
    /** 最近消费时间戳（毫秒）。 */
    val lastTimestamp: Long,
    /** 已订阅的 Topic 列表。 */
    val topics: List<String>
)

/** 消费组详情：在线连接、订阅与各队列消费进度。 */
data class RocketMQConsumerGroupDetail(
    /** 消费者组名称。 */
    val group: String,
    /** 消费者组是否在线。 */
    val online: Boolean,
    /** 当前在线客户端连接列表。 */
    val connections: List<RocketMQConsumerConnectionView>,
    /** 已订阅的 Topic 列表。 */
    val topics: List<String>,
    /** 各队列消费进度。 */
    val progress: List<RocketMQConsumeProgressView>
)

/** 消费组在线客户端连接信息。 */
data class RocketMQConsumerConnectionView(
    /** 客户端版本。 */
    val version: String,
    /** 客户端编程语言。 */
    val language: String,
    /** 客户端唯一标识。 */
    val clientId: String,
    /** 客户端网络地址。 */
    val clientAddr: String
)

/** 单个队列的消费进度。 */
data class RocketMQConsumeProgressView(
    /** Topic 名称。 */
    val topic: String,
    /** Broker 名称。 */
    val brokerName: String,
    /** 队列 ID。 */
    val queueId: Int,
    /** Broker 当前最大位点。 */
    val brokerOffset: Long,
    /** 消费者组当前消费位点。 */
    val consumerOffset: Long,
    /** 消息积压数量。 */
    val diff: Long,
    /** 位点最后更新时间戳（毫秒）。 */
    val lastTimestamp: Long,
    /** 对应客户端标识。 */
    val clientId: String
)

/** 消息查询结果列表项；仅包含索引字段，正文详情需按消息 ID 另行加载。 */
data class RocketMQMessageListItem(
    /** 消息 ID。 */
    val msgId: String,
    /** Topic 名称。 */
    val topic: String,
    /** 消息标签。 */
    val tags: String?,
    /** 消息业务键。 */
    val keys: String?,
    /** 消息生产者地址。 */
    val bornHost: String,
    /** 消息存储 Broker 地址。 */
    val storeHost: String,
    /** 队列 ID。 */
    val queueId: Int,
    /** 消息在队列中的位点。 */
    val queueOffset: Long,
    /** 消息生产时间戳（毫秒）。 */
    val bornTimestamp: Long,
    /** 消息存储时间戳（毫秒）。 */
    val storeTimestamp: Long,
    /** 消息重试消费次数。 */
    val reconsumeTimes: Int,
    /** 是否为死信消息。 */
    val deadLetter: Boolean
)

/** 消息完整详情，含正文文本与十六进制内容。 */
data class RocketMQMessageView(
    /** 消息 ID。 */
    val msgId: String,
    /** Topic 名称。 */
    val topic: String,
    /** 消息标签。 */
    val tags: String?,
    /** 消息业务键。 */
    val keys: String?,
    /** 消息生产者地址。 */
    val bornHost: String,
    /** 消息存储 Broker 地址。 */
    val storeHost: String,
    /** 队列 ID。 */
    val queueId: Int,
    /** 消息在队列中的位点。 */
    val queueOffset: Long,
    /** 消息生产时间戳（毫秒）。 */
    val bornTimestamp: Long,
    /** 消息存储时间戳（毫秒）。 */
    val storeTimestamp: Long,
    /** 消息重试消费次数。 */
    val reconsumeTimes: Int,
    /** 延迟消息级别；0 表示立即投递。 */
    val delayLevel: Int,
    /** 消息正文大小（字节）。 */
    val bodySize: Int,
    /** 可读的消息正文；二进制正文时为空。 */
    val bodyText: String?,
    /** 消息正文的十六进制表示。 */
    val bodyHex: String,
    /** 消息自定义属性。 */
    val properties: Map<String, String>,
    /** 是否为死信消息。 */
    val deadLetter: Boolean
)

/** 重发消息结果。 */
data class RocketMQResendResult(
    /** 重发后生成的消息 ID。 */
    val msgId: String,
    /** 原 Topic 名称。 */
    val originalTopic: String,
    /** 重发目标 Topic 名称。 */
    val targetTopic: String,
    /** 重发消息的延迟级别。 */
    val delayLevel: Int
)

/** 发送测试消息结果。 */
data class RocketMQSendResult(
    /** 客户端消息 ID。 */
    val msgId: String,
    /** Broker 存储消息 ID。 */
    val offsetMsgId: String,
    /** 消息发送的目标 Topic。 */
    val topic: String
)
