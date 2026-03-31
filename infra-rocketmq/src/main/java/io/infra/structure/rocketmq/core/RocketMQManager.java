package io.infra.structure.rocketmq.core;

import io.infra.structure.rocketmq.constants.Const;
import org.apache.rocketmq.client.producer.DefaultMQProducer;

import java.util.Collections;
import java.util.Map;

/**
 * @author codex
 */
public class RocketMQManager {
    private final Map<String, DefaultMQProducer> producers;
    private final Map<String, RocketMQConsumerFactory> consumerFactories;

    public RocketMQManager(Map<String, DefaultMQProducer> producers,
                           Map<String, RocketMQConsumerFactory> consumerFactories) {
        this.producers = producers == null ? Collections.emptyMap() : producers;
        this.consumerFactories = consumerFactories == null ? Collections.emptyMap() : consumerFactories;
    }

    public DefaultMQProducer getProducer(String name) {
        DefaultMQProducer producer = producers.get(name);
        if (producer != null) {
            return producer;
        }
        return producers.get(name + Const.producerBeanSuffix);
    }

    public RocketMQConsumerFactory getConsumerFactory(String name) {
        RocketMQConsumerFactory factory = consumerFactories.get(name);
        if (factory != null) {
            return factory;
        }
        return consumerFactories.get(name + Const.consumerFactoryBeanSuffix);
    }

    public Map<String, DefaultMQProducer> getProducers() {
        return Collections.unmodifiableMap(producers);
    }

    public Map<String, RocketMQConsumerFactory> getConsumerFactories() {
        return Collections.unmodifiableMap(consumerFactories);
    }
}
