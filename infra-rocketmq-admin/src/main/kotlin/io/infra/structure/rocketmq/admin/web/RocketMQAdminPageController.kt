package io.infra.structure.rocketmq.admin.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/** 渲染 RocketMQ 管理后台的 Thymeleaf 页面。 */
@Controller
class RocketMQAdminPageController {

    /** 仪表盘：集群概览。 */
    @GetMapping(RocketMQAdminWebPaths.ROOT)
    fun dashboard(model: Model): String {
        model.addAttribute("page", "dashboard")
        return "rocketmq-dashboard"
    }

    /** Topic 管理页。 */
    @GetMapping(RocketMQAdminWebPaths.TOPICS)
    fun topics(model: Model): String {
        model.addAttribute("page", "topics")
        return "rocketmq-topics"
    }

    /** Topic 详情页。 */
    @GetMapping(RocketMQAdminWebPaths.TOPIC_DETAIL)
    fun topicDetail(model: Model, @RequestParam("name") topic: String): String {
        model.addAttribute("page", "topics")
        model.addAttribute("topicName", topic)
        return "rocketmq-topic-detail"
    }

    /** 消费组管理页。 */
    @GetMapping(RocketMQAdminWebPaths.CONSUMER_GROUPS)
    fun consumerGroups(model: Model): String {
        model.addAttribute("page", "consumer-groups")
        return "rocketmq-consumer-groups"
    }

    /** 消费组详情页。 */
    @GetMapping(RocketMQAdminWebPaths.CONSUMER_GROUP_DETAIL)
    fun consumerGroupDetail(model: Model, @RequestParam("name") group: String): String {
        model.addAttribute("page", "consumer-groups")
        model.addAttribute("groupName", group)
        return "rocketmq-consumer-group-detail"
    }

    /** 消息查询页。 */
    @GetMapping(RocketMQAdminWebPaths.MESSAGE_QUERY)
    fun messageQuery(model: Model): String {
        model.addAttribute("page", "messages")
        return "rocketmq-message-query"
    }

    /** 消息详情页。 */
    @GetMapping(RocketMQAdminWebPaths.MESSAGE_DETAIL)
    fun messageDetail(
        model: Model,
        @RequestParam("id") msgId: String,
        @RequestParam("topic") topic: String
    ): String {
        model.addAttribute("page", "messages")
        model.addAttribute("msgId", msgId)
        model.addAttribute("topic", topic)
        return "rocketmq-message-detail"
    }
}