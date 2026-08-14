package io.infra.structure.rocketmq.admin.autoconfigure

import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import io.infra.structure.rocketmq.admin.service.RocketMQClusterService
import io.infra.structure.rocketmq.admin.service.RocketMQConsumerGroupService
import io.infra.structure.rocketmq.admin.service.RocketMQMessageService
import io.infra.structure.rocketmq.admin.service.RocketMQTopicService
import io.infra.structure.rocketmq.admin.support.RocketMQAdminClient
import io.infra.structure.rocketmq.admin.web.RocketMQAdminAccessInterceptor
import io.infra.structure.rocketmq.admin.web.RocketMQAdminApiController
import io.infra.structure.rocketmq.admin.web.RocketMQAdminApiExceptionHandler
import io.infra.structure.rocketmq.admin.web.RocketMQAdminPageController
import io.infra.structure.rocketmq.admin.web.RocketMQAdminPageExceptionHandler
import io.infra.structure.rocketmq.admin.web.RocketMQAdminPageModelAdvice
import io.infra.structure.rocketmq.admin.web.RocketMQAdminWebConfigurer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * RocketMQ 管理后台自动配置：管理客户端、服务与 Web 层。
 *
 * 关闭鉴权时管理 API 直接暴露，禁止在生产环境关闭 [RocketMQAdminProperties.authEnabled]。
 */
@AutoConfiguration
@EnableConfigurationProperties(RocketMQAdminProperties::class)
@ConditionalOnProperty(prefix = "infra.rocketmq.admin", name = ["enabled"], havingValue = "true")
class InfraRocketMQAdminAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    fun rocketMQAdminClient(properties: RocketMQAdminProperties): RocketMQAdminClient =
        RocketMQAdminClient(properties)

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQClusterService(
        client: RocketMQAdminClient,
        properties: RocketMQAdminProperties
    ): RocketMQClusterService = RocketMQClusterService(client, properties)

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQTopicService(
        client: RocketMQAdminClient,
        clusterService: RocketMQClusterService,
        properties: RocketMQAdminProperties
    ): RocketMQTopicService = RocketMQTopicService(client, clusterService, properties)

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQConsumerGroupService(
        client: RocketMQAdminClient,
        properties: RocketMQAdminProperties
    ): RocketMQConsumerGroupService = RocketMQConsumerGroupService(client, properties)

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQMessageService(
        client: RocketMQAdminClient,
        properties: RocketMQAdminProperties
    ): RocketMQMessageService = RocketMQMessageService(client, properties)

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQAdminPageController(): RocketMQAdminPageController = RocketMQAdminPageController()

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQAdminApiController(
        clusterService: RocketMQClusterService,
        topicService: RocketMQTopicService,
        consumerGroupService: RocketMQConsumerGroupService,
        messageService: RocketMQMessageService
    ): RocketMQAdminApiController = RocketMQAdminApiController(
        clusterService, topicService, consumerGroupService, messageService
    )

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQAdminPageModelAdvice(properties: RocketMQAdminProperties): RocketMQAdminPageModelAdvice =
        RocketMQAdminPageModelAdvice(properties)

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQAdminApiExceptionHandler(): RocketMQAdminApiExceptionHandler = RocketMQAdminApiExceptionHandler()

    @Bean
    @ConditionalOnMissingBean
    fun rocketMQAdminPageExceptionHandler(): RocketMQAdminPageExceptionHandler = RocketMQAdminPageExceptionHandler()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.rocketmq.admin", name = ["auth-enabled"], havingValue = "true")
    fun rocketMQAdminAccessInterceptor(properties: RocketMQAdminProperties): RocketMQAdminAccessInterceptor =
        RocketMQAdminAccessInterceptor(properties)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.rocketmq.admin", name = ["auth-enabled"], havingValue = "true")
    fun rocketMQAdminWebConfigurer(accessInterceptor: RocketMQAdminAccessInterceptor): RocketMQAdminWebConfigurer =
        RocketMQAdminWebConfigurer(accessInterceptor)
}