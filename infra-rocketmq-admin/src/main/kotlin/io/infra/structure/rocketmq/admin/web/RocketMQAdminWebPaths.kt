package io.infra.structure.rocketmq.admin.web

/** 管理后台页面与 API 路径，避免控制器内散落 URL 字面量。 */
object RocketMQAdminWebPaths {

    /** 页面根路径（仪表盘）。 */
    const val ROOT = "/"

    /** Topic 管理页。 */
    const val TOPICS = "/topics"

    /** Topic 详情页。 */
    const val TOPIC_DETAIL = "/topics/detail"

    /** 消费组管理页。 */
    const val CONSUMER_GROUPS = "/consumer-groups"

    /** 消费组详情页。 */
    const val CONSUMER_GROUP_DETAIL = "/consumer-groups/detail"

    /** 消息查询页。 */
    const val MESSAGE_QUERY = "/messages"

    /** 消息详情页。 */
    const val MESSAGE_DETAIL = "/messages/detail"

    /** 管理 REST API 根路径。 */
    const val API_ROOT = "/api/rocketmq"

    /** 集群概览。 */
    const val API_CLUSTER = "/cluster"

    /** Topic 列表 / 创建。 */
    const val API_TOPICS = "/topics"

    /** Topic 详情 / 删除。 */
    const val API_TOPIC_BY_NAME = "/topics/{topic}"

    /** 消费组列表。 */
    const val API_CONSUMER_GROUPS = "/consumer-groups"

    /** 消费组详情。 */
    const val API_CONSUMER_GROUP_BY_NAME = "/consumer-groups/{group}"

    /** 消费组位点重置。 */
    const val API_CONSUMER_GROUP_RESET = "/consumer-groups/{group}/reset"

    /** 消息索引查询。 */
    const val API_MESSAGE_QUERY = "/messages/query"

    /** 消息详情。 */
    const val API_MESSAGE_DETAIL = "/messages/{msgId}"

    /** 消息重发。 */
    const val API_MESSAGE_RESEND = "/messages/{msgId}/resend"

    /** 发送测试消息。 */
    const val API_MESSAGE_SEND = "/messages/send"
}