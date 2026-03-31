package io.infra.structure.rocketmq.core;

import io.infra.structure.rocketmq.properties.RocketMQConsumerProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author codex
 */
@Slf4j
@Getter
public class DefaultRocketMQConsumerFactory implements RocketMQConsumerFactory, DisposableBean {
    private final String name;
    private final RocketMQConsumerProperties properties;
    private final List<DefaultMQPushConsumer> createdConsumers = new CopyOnWriteArrayList<>();

    public DefaultRocketMQConsumerFactory(String name, RocketMQConsumerProperties properties) {
        this.name = name;
        this.properties = properties;
    }

    @Override
    public RocketMQConsumerProperties getProperties() {
        return properties;
    }

    @Override
    public DefaultMQPushConsumer createConsumer() {
        DefaultMQPushConsumer consumer = RocketMQClientSupport.buildConsumer(properties);
        createdConsumers.add(consumer);
        return consumer;
    }

    @Override
    public DefaultMQPushConsumer createAndStart(String topic, String subExpression,
                                                MessageListenerConcurrently listener) throws MQClientException {
        DefaultMQPushConsumer consumer = createConsumer();
        consumer.subscribe(topic, subExpression);
        consumer.registerMessageListener(listener);
        consumer.start();
        log.info("RocketMQ consumer [{}] started, topic: {}, expression: {}", name, topic, subExpression);
        return consumer;
    }

    @Override
    public DefaultMQPushConsumer createAndStart(String topic, String subExpression,
                                                MessageListenerOrderly listener) throws MQClientException {
        DefaultMQPushConsumer consumer = createConsumer();
        consumer.subscribe(topic, subExpression);
        consumer.registerMessageListener(listener);
        consumer.start();
        log.info("RocketMQ consumer [{}] started orderly, topic: {}, expression: {}", name, topic, subExpression);
        return consumer;
    }

    @Override
    public DefaultMQPushConsumer createAndStart(String topic, MessageSelector selector,
                                                MessageListenerConcurrently listener) throws MQClientException {
        DefaultMQPushConsumer consumer = createConsumer();
        consumer.subscribe(topic, selector);
        consumer.registerMessageListener(listener);
        consumer.start();
        log.info("RocketMQ consumer [{}] started with selector, topic: {}", name, topic);
        return consumer;
    }

    @Override
    public DefaultMQPushConsumer createAndStart(String topic, MessageSelector selector,
                                                MessageListenerOrderly listener) throws MQClientException {
        DefaultMQPushConsumer consumer = createConsumer();
        consumer.subscribe(topic, selector);
        consumer.registerMessageListener(listener);
        consumer.start();
        log.info("RocketMQ consumer [{}] started orderly with selector, topic: {}", name, topic);
        return consumer;
    }

    @Override
    public void destroy() {
        for (DefaultMQPushConsumer consumer : createdConsumers) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.warn("RocketMQ consumer [{}] shutdown failed", name, e);
            }
        }
        createdConsumers.clear();
    }
}
