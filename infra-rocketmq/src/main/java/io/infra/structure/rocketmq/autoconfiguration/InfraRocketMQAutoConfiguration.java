package io.infra.structure.rocketmq.autoconfiguration;

import io.infra.structure.core.tool.BinderTool;
import io.infra.structure.rocketmq.constants.Const;
import io.infra.structure.rocketmq.core.RocketMQConsumerFactory;
import io.infra.structure.rocketmq.core.RocketMQManager;
import io.infra.structure.rocketmq.definition.RocketMQBeanDefinitionRegistry;
import io.infra.structure.rocketmq.listener.RocketMQListenerAnnotationProcessor;
import io.infra.structure.rocketmq.properties.RocketMQConfig;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.Map;

/**
 * @author codex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = Const.configPrefix, value = "enabled", havingValue = "true")
public class InfraRocketMQAutoConfiguration {

    @Bean
    public static RocketMQConfig rocketMQConfig(Environment env) {
        return BinderTool.bind(env, Const.configPrefix, RocketMQConfig.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public static RocketMQBeanDefinitionRegistry rocketMQBeanDefinitionRegistry(RocketMQConfig rocketMQConfig) {
        return new RocketMQBeanDefinitionRegistry(rocketMQConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public RocketMQManager rocketMQManager(ObjectProvider<Map<String, DefaultMQProducer>> producersProvider,
                                           ObjectProvider<Map<String, RocketMQConsumerFactory>> consumerFactoriesProvider) {
        return new RocketMQManager(
                producersProvider.getIfAvailable(Collections::emptyMap),
                consumerFactoriesProvider.getIfAvailable(Collections::emptyMap)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public RocketMQListenerAnnotationProcessor rocketMQListenerAnnotationProcessor(RocketMQManager rocketMQManager,
                                                                                   ObjectProvider<RocketMQConsumerFactory> primaryConsumerFactoryProvider,
                                                                                   Environment environment) {
        return new RocketMQListenerAnnotationProcessor(rocketMQManager, primaryConsumerFactoryProvider, environment);
    }
}
