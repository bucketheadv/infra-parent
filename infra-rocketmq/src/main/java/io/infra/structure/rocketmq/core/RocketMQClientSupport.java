package io.infra.structure.rocketmq.core;

import io.infra.structure.rocketmq.properties.RocketMQBaseProperties;
import io.infra.structure.rocketmq.properties.RocketMQConsumerProperties;
import io.infra.structure.rocketmq.properties.RocketMQProducerProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;

import java.util.Locale;

/**
 * @author codex
 */
public final class RocketMQClientSupport {
    private RocketMQClientSupport() {
    }

    public static DefaultMQProducer buildProducer(RocketMQProducerProperties properties) {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
        applyCommonConfig(producer, properties);
        if (properties.getSendMsgTimeout() != null) {
            producer.setSendMsgTimeout(properties.getSendMsgTimeout());
        }
        if (properties.getCompressMsgBodyOverHowmuch() != null) {
            producer.setCompressMsgBodyOverHowmuch(properties.getCompressMsgBodyOverHowmuch());
        }
        if (properties.getMaxMessageSize() != null) {
            producer.setMaxMessageSize(properties.getMaxMessageSize());
        }
        if (properties.getRetryTimesWhenSendFailed() != null) {
            producer.setRetryTimesWhenSendFailed(properties.getRetryTimesWhenSendFailed());
        }
        if (properties.getRetryTimesWhenSendAsyncFailed() != null) {
            producer.setRetryTimesWhenSendAsyncFailed(properties.getRetryTimesWhenSendAsyncFailed());
        }
        if (properties.getRetryAnotherBrokerWhenNotStoreOK() != null) {
            producer.setRetryAnotherBrokerWhenNotStoreOK(properties.getRetryAnotherBrokerWhenNotStoreOK());
        }
        return producer;
    }

    public static DefaultMQPushConsumer buildConsumer(RocketMQConsumerProperties properties) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getConsumerGroup());
        applyCommonConfig(consumer, properties);
        if (StringUtils.isNotBlank(properties.getMessageModel())) {
            consumer.setMessageModel(MessageModel.valueOf(properties.getMessageModel().trim().toUpperCase(Locale.ROOT)));
        }
        if (StringUtils.isNotBlank(properties.getConsumeFromWhere())) {
            consumer.setConsumeFromWhere(ConsumeFromWhere.valueOf(properties.getConsumeFromWhere().trim().toUpperCase(Locale.ROOT)));
        }
        if (properties.getConsumeThreadMin() != null) {
            consumer.setConsumeThreadMin(properties.getConsumeThreadMin());
        }
        if (properties.getConsumeThreadMax() != null) {
            consumer.setConsumeThreadMax(properties.getConsumeThreadMax());
        }
        if (properties.getPullBatchSize() != null) {
            consumer.setPullBatchSize(properties.getPullBatchSize());
        }
        if (properties.getConsumeMessageBatchMaxSize() != null) {
            consumer.setConsumeMessageBatchMaxSize(properties.getConsumeMessageBatchMaxSize());
        }
        if (properties.getMaxReconsumeTimes() != null) {
            consumer.setMaxReconsumeTimes(properties.getMaxReconsumeTimes());
        }
        if (properties.getConsumeTimeout() != null) {
            consumer.setConsumeTimeout(properties.getConsumeTimeout());
        }
        if (properties.getSuspendCurrentQueueTimeMillis() != null) {
            consumer.setSuspendCurrentQueueTimeMillis(properties.getSuspendCurrentQueueTimeMillis());
        }
        if (properties.getAwaitTerminationMillisWhenShutdown() != null) {
            consumer.setAwaitTerminationMillisWhenShutdown(properties.getAwaitTerminationMillisWhenShutdown());
        }
        return consumer;
    }

    private static void applyCommonConfig(ClientConfig clientConfig, RocketMQBaseProperties properties) {
        clientConfig.setNamesrvAddr(properties.getNamesrvAddr());
        if (StringUtils.isNotBlank(properties.getNamespace())) {
            clientConfig.setNamespaceV2(properties.getNamespace());
        }
        if (StringUtils.isNotBlank(properties.getInstanceName())) {
            clientConfig.setInstanceName(properties.getInstanceName());
        }
        if (StringUtils.isNotBlank(properties.getAccessChannel())) {
            clientConfig.setAccessChannel(AccessChannel.valueOf(properties.getAccessChannel().trim().toUpperCase(Locale.ROOT)));
        }
        if (properties.getVipChannelEnabled() != null) {
            clientConfig.setVipChannelEnabled(properties.getVipChannelEnabled());
        }
        if (properties.getUseTLS() != null) {
            clientConfig.setUseTLS(properties.getUseTLS());
        }
        if (properties.getEnableMsgTrace() != null) {
            clientConfig.setEnableTrace(properties.getEnableMsgTrace());
        }
        if (StringUtils.isNotBlank(properties.getCustomizedTraceTopic())) {
            clientConfig.setTraceTopic(properties.getCustomizedTraceTopic());
        }
    }
}
