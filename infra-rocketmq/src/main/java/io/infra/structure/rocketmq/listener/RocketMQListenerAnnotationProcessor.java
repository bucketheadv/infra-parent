package io.infra.structure.rocketmq.listener;

import io.infra.structure.core.tool.BeanTool;
import io.infra.structure.core.tool.JsonTool;
import io.infra.structure.rocketmq.annotation.InfraRocketMQConsumer;
import io.infra.structure.rocketmq.core.RocketMQClientSupport;
import io.infra.structure.rocketmq.core.RocketMQConsumerFactory;
import io.infra.structure.rocketmq.core.RocketMQManager;
import io.infra.structure.rocketmq.properties.RocketMQConsumerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQReplyListener;
import org.apache.rocketmq.spring.support.RocketMQUtil;
import org.jspecify.annotations.NonNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 兼容 RocketMQ 官方监听注解，但底层仍然使用 infra-rocketmq 的多实例 consumer 配置。
 *
 * @author codex
 */
@Slf4j
public class RocketMQListenerAnnotationProcessor implements SmartInitializingSingleton, ApplicationContextAware, DisposableBean {
    private final RocketMQManager rocketMQManager;
    private final ObjectProvider<RocketMQConsumerFactory> primaryConsumerFactoryProvider;
    private final Environment environment;
    private final List<DefaultMQPushConsumer> consumers = new CopyOnWriteArrayList<>();
    private ApplicationContext applicationContext;

    public RocketMQListenerAnnotationProcessor(RocketMQManager rocketMQManager,
                                               ObjectProvider<RocketMQConsumerFactory> primaryConsumerFactoryProvider,
                                               Environment environment) {
        this.rocketMQManager = rocketMQManager;
        this.primaryConsumerFactoryProvider = primaryConsumerFactoryProvider;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RocketMQMessageListener.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            registerListener(entry.getKey(), entry.getValue());
        }
    }

    private void registerListener(String beanName, Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        RocketMQMessageListener annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, RocketMQMessageListener.class);
        if (annotation == null) {
            return;
        }
        if (!(bean instanceof RocketMQListener)) {
            throw new IllegalStateException("Bean " + beanName + " 使用了 @RocketMQMessageListener，但未实现 RocketMQListener");
        }
        if (bean instanceof RocketMQReplyListener) {
            throw new IllegalStateException("Bean " + beanName + " 当前暂不支持 RocketMQReplyListener");
        }
        RocketMQConsumerFactory factory = resolveConsumerFactory(targetClass);
        RocketMQConsumerProperties properties = mergeProperties(factory, annotation);
        validate(annotation, properties, beanName);
        DefaultMQPushConsumer consumer = RocketMQClientSupport.buildConsumer(properties);
        Class<?> payloadType = resolvePayloadType(targetClass);
        try {
            subscribeAndRegisterListener(beanName, consumer, annotation, (RocketMQListener<?>) bean, payloadType);
            consumer.start();
            consumers.add(consumer);
            log.info("RocketMQ 注解监听器 [{}] 注册成功, topic: {}, consumerGroup: {}, config: {}",
                    beanName, resolve(annotation.topic()), properties.getConsumerGroup(), factory.getName());
        } catch (Exception e) {
            try {
                consumer.shutdown();
            } catch (Exception shutdownEx) {
                log.warn("RocketMQ 注解监听器 [{}] 初始化失败后关闭 consumer 异常", beanName, shutdownEx);
            }
            throw new IllegalStateException("RocketMQ 注解监听器注册失败: " + beanName, e);
        }
    }

    private RocketMQConsumerFactory resolveConsumerFactory(Class<?> targetClass) {
        InfraRocketMQConsumer binding = AnnotatedElementUtils.findMergedAnnotation(targetClass, InfraRocketMQConsumer.class);
        if (binding != null && StringUtils.isNotBlank(resolve(binding.value()))) {
            RocketMQConsumerFactory factory = rocketMQManager.getConsumerFactory(resolve(binding.value()));
            if (factory == null) {
                throw new IllegalStateException("未找到 RocketMQ consumer 配置: " + resolve(binding.value()));
            }
            return factory;
        }
        RocketMQConsumerFactory primaryFactory = primaryConsumerFactoryProvider.getIfAvailable();
        if (primaryFactory != null) {
            return primaryFactory;
        }
        throw new IllegalStateException("未指定 @InfraRocketMQConsumer，且不存在 primary RocketMQConsumerFactory");
    }

    private RocketMQConsumerProperties mergeProperties(RocketMQConsumerFactory factory, RocketMQMessageListener annotation) {
        RocketMQConsumerProperties properties = BeanTool.copyAs(factory.getProperties(), RocketMQConsumerProperties.class);
        properties.setConsumerGroup(firstNonBlank(resolve(annotation.consumerGroup()), properties.getConsumerGroup()));
        properties.setNamesrvAddr(firstNonBlank(resolve(annotation.nameServer()), properties.getNamesrvAddr()));
        properties.setNamespace(firstNonBlank(resolve(annotation.namespace()), properties.getNamespace()));
        properties.setMessageModel(annotation.messageModel().name());
        properties.setConsumeThreadMin(annotation.consumeThreadNumber());
        properties.setConsumeThreadMax(resolveConsumeThreadMax(annotation));
        properties.setMaxReconsumeTimes(annotation.maxReconsumeTimes());
        properties.setConsumeTimeout(annotation.consumeTimeout());
        properties.setSuspendCurrentQueueTimeMillis((long) annotation.suspendCurrentQueueTimeMillis());
        properties.setAccessChannel(firstNonBlank(resolve(annotation.accessChannel()), properties.getAccessChannel()));
        properties.setUseTLS(Boolean.parseBoolean(resolve(annotation.tlsEnable())));
        properties.setEnableMsgTrace(annotation.enableMsgTrace());
        properties.setCustomizedTraceTopic(firstNonBlank(resolve(annotation.customizedTraceTopic()), properties.getCustomizedTraceTopic()));
        properties.setAwaitTerminationMillisWhenShutdown((long) annotation.awaitTerminationMillisWhenShutdown());
        return properties;
    }

    private int resolveConsumeThreadMax(RocketMQMessageListener annotation) {
        return Math.max(annotation.consumeThreadNumber(), annotation.consumeThreadMax());
    }

    private void validate(RocketMQMessageListener annotation, RocketMQConsumerProperties properties, String beanName) {
        if (StringUtils.isBlank(properties.getConsumerGroup())) {
            throw new IllegalStateException("Bean " + beanName + " 未配置 consumerGroup");
        }
        if (StringUtils.isBlank(resolve(annotation.topic()))) {
            throw new IllegalStateException("Bean " + beanName + " 未配置 topic");
        }
        if (StringUtils.isBlank(properties.getNamesrvAddr())) {
            throw new IllegalStateException("Bean " + beanName + " 未配置 namesrvAddr");
        }
        if (StringUtils.isNotBlank(resolve(annotation.accessKey())) || StringUtils.isNotBlank(resolve(annotation.secretKey()))) {
            log.warn("Bean [{}] 使用了 accessKey/secretKey，但 infra-rocketmq 注解模式暂未支持 ACL 构造器，将忽略该配置", beanName);
        }
    }

    private void subscribeAndRegisterListener(String beanName, DefaultMQPushConsumer consumer,
                                              RocketMQMessageListener annotation,
                                              RocketMQListener<?> listener, Class<?> payloadType) throws MQClientException {
        String topic = resolve(annotation.topic());
        String selectorExpression = firstNonBlank(resolve(annotation.selectorExpression()), "*");
        if (annotation.selectorType() == SelectorType.SQL92) {
            consumer.subscribe(topic, MessageSelector.bySql(selectorExpression));
        } else {
            consumer.subscribe(topic, MessageSelector.byTag(selectorExpression));
        }

        if (annotation.consumeMode() == ConsumeMode.ORDERLY) {
            consumer.registerMessageListener((MessageListenerOrderly) (msgs, context) -> {
                for (MessageExt messageExt : msgs) {
                    try {
                        invokeListener(listener, payloadType, messageExt);
                    } catch (Exception e) {
                        log.error("RocketMQ 注解监听器 [{}] 消费失败, msgId: {}", beanName, messageExt.getMsgId(), e);
                        return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                    }
                }
                return ConsumeOrderlyStatus.SUCCESS;
            });
            return;
        }

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt messageExt : msgs) {
                try {
                    invokeListener(listener, payloadType, messageExt);
                } catch (Exception e) {
                    log.error("RocketMQ 注解监听器 [{}] 消费失败, msgId: {}", beanName, messageExt.getMsgId(), e);
                    if (annotation.delayLevelWhenNextConsume() > 0) {
                        context.setDelayLevelWhenNextConsume(annotation.delayLevelWhenNextConsume());
                    }
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
    }

    @SuppressWarnings("unchecked")
    private void invokeListener(RocketMQListener<?> listener, Class<?> payloadType, MessageExt messageExt) {
        Object payload = convertMessage(payloadType, messageExt);
        ((RocketMQListener<Object>) listener).onMessage(payload);
    }

    private Object convertMessage(Class<?> payloadType, MessageExt messageExt) {
        if (payloadType == null || Object.class.equals(payloadType) || String.class.equals(payloadType)) {
            return new String(messageExt.getBody(), StandardCharsets.UTF_8);
        }
        if (byte[].class.equals(payloadType)) {
            return messageExt.getBody();
        }
        if (MessageExt.class.isAssignableFrom(payloadType)) {
            return messageExt;
        }
        if (Message.class.isAssignableFrom(payloadType)) {
            return RocketMQUtil.convertToSpringMessage(messageExt);
        }
        return JsonTool.parseObject(new String(messageExt.getBody(), StandardCharsets.UTF_8), payloadType);
    }

    private Class<?> resolvePayloadType(Class<?> targetClass) {
        ResolvableType resolvableType = ResolvableType.forClass(targetClass).as(RocketMQListener.class);
        return resolvableType.getGeneric(0).resolve(Object.class);
    }

    private String resolve(String value) {
        if (value == null) {
            return null;
        }
        return environment.resolvePlaceholders(value).trim();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void destroy() {
        for (DefaultMQPushConsumer consumer : consumers) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.warn("RocketMQ 注解监听器关闭异常", e);
            }
        }
        consumers.clear();
    }
}
