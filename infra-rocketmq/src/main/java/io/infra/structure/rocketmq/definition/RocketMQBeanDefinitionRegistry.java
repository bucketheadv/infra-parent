package io.infra.structure.rocketmq.definition;

import io.infra.structure.rocketmq.constants.Const;
import io.infra.structure.rocketmq.core.DefaultRocketMQConsumerFactory;
import io.infra.structure.rocketmq.properties.RocketMQConfig;
import io.infra.structure.rocketmq.properties.RocketMQConsumerProperties;
import io.infra.structure.rocketmq.properties.RocketMQProducerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.util.Collections;
import java.util.Map;

import static io.infra.structure.rocketmq.core.RocketMQClientSupport.buildProducer;

/**
 * @author codex
 */
@Slf4j
public class RocketMQBeanDefinitionRegistry implements BeanDefinitionRegistryPostProcessor {
    private final RocketMQConfig rocketMQConfig;

    public RocketMQBeanDefinitionRegistry(RocketMQConfig rocketMQConfig) {
        this.rocketMQConfig = rocketMQConfig;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        registerProducers(beanDefinitionRegistry);
        registerConsumerFactories(beanDefinitionRegistry);
    }

    private void registerProducers(BeanDefinitionRegistry registry) {
        Map<String, RocketMQProducerProperties> producers =
                rocketMQConfig.getProducers() == null ? Collections.emptyMap() : rocketMQConfig.getProducers();
        String primaryKey = resolvePrimaryKey(producers, rocketMQConfig.getPrimaryProducer());
        for (Map.Entry<String, RocketMQProducerProperties> entry : producers.entrySet()) {
            String key = entry.getKey();
            RocketMQProducerProperties properties = entry.getValue();
            String beanName = key + Const.producerBeanSuffix;
            boolean primary = key.equals(primaryKey);
            registry.registerBeanDefinition(beanName, BeanDefinitionBuilder
                    .genericBeanDefinition(DefaultMQProducer.class, () -> buildProducer(properties))
                    .setPrimary(primary)
                    .setInitMethodName("start")
                    .setDestroyMethodName("shutdown")
                    .getBeanDefinition());
            log.info("Bean: {} 注册成功", beanName);
        }
        if (!producers.isEmpty()) {
            log.info("加载 RocketMQ producer {} 个, primary producer 名称为 [{}]", producers.size(), primaryKey);
        }
    }

    private void registerConsumerFactories(BeanDefinitionRegistry registry) {
        Map<String, RocketMQConsumerProperties> consumers =
                rocketMQConfig.getConsumers() == null ? Collections.emptyMap() : rocketMQConfig.getConsumers();
        String primaryKey = resolvePrimaryKey(consumers, rocketMQConfig.getPrimaryConsumer());
        for (Map.Entry<String, RocketMQConsumerProperties> entry : consumers.entrySet()) {
            String key = entry.getKey();
            RocketMQConsumerProperties properties = entry.getValue();
            String beanName = key + Const.consumerFactoryBeanSuffix;
            boolean primary = key.equals(primaryKey);
            registry.registerBeanDefinition(beanName, BeanDefinitionBuilder
                    .genericBeanDefinition(DefaultRocketMQConsumerFactory.class,
                            () -> new DefaultRocketMQConsumerFactory(key, properties))
                    .setPrimary(primary)
                    .setDestroyMethodName("destroy")
                    .getBeanDefinition());
            log.info("RocketMQ Bean: {} 注册成功", beanName);
        }
        if (!consumers.isEmpty()) {
            log.info("加载 RocketMQ consumer factory {} 个, primary consumer 名称为 [{}]", consumers.size(), primaryKey);
        }
    }

    private <T> String resolvePrimaryKey(Map<String, T> templates, String primary) {
        if (templates.isEmpty()) {
            return null;
        }
        if (primary != null && templates.containsKey(primary)) {
            return primary;
        }
        return templates.keySet().iterator().next();
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }
}
