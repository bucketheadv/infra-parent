package io.infra.structure.rocketmq.core;

import io.infra.structure.rocketmq.properties.RocketMQConsumerProperties;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;

/**
 * @author codex
 */
public interface RocketMQConsumerFactory {
    String getName();

    RocketMQConsumerProperties getProperties();

    DefaultMQPushConsumer createConsumer();

    DefaultMQPushConsumer createAndStart(String topic, String subExpression,
                                         MessageListenerConcurrently listener) throws MQClientException;

    DefaultMQPushConsumer createAndStart(String topic, String subExpression,
                                         MessageListenerOrderly listener) throws MQClientException;

    DefaultMQPushConsumer createAndStart(String topic, MessageSelector selector,
                                         MessageListenerConcurrently listener) throws MQClientException;

    DefaultMQPushConsumer createAndStart(String topic, MessageSelector selector,
                                         MessageListenerOrderly listener) throws MQClientException;
}
