# infra-rocketmq 使用说明

## 概述

`infra-rocketmq` 参考 `infra-redis` 的实现方式，支持：

- 在 `application.properties` 中配置多个 RocketMQ 实例
- 自动注册多个 Producer Bean
- 自动注册多个 ConsumerFactory Bean
- 在业务代码中按名称动态选择 Producer 或 Consumer
- 通过 `primaryProducer` 和 `primaryConsumer` 指定默认实例
- 兼容 `@RocketMQMessageListener` 注解监听

## 配置说明

配置前缀为 `infra.rocketmq`。

### 顶层配置

```properties
infra.rocketmq.enabled=true
infra.rocketmq.primaryProducer=order
infra.rocketmq.primaryConsumer=core
```

### 多 Producer 配置

```properties
infra.rocketmq.producers.order.namesrvAddr=127.0.0.1:9876
infra.rocketmq.producers.order.producerGroup=order_producer_group
infra.rocketmq.producers.order.instanceName=order-producer
infra.rocketmq.producers.order.namespace=
infra.rocketmq.producers.order.vipChannelEnabled=false
infra.rocketmq.producers.order.useTLS=false
infra.rocketmq.producers.order.sendMsgTimeout=3000
infra.rocketmq.producers.order.compressMsgBodyOverHowmuch=4096
infra.rocketmq.producers.order.maxMessageSize=4194304
infra.rocketmq.producers.order.retryTimesWhenSendFailed=2
infra.rocketmq.producers.order.retryTimesWhenSendAsyncFailed=2
infra.rocketmq.producers.order.retryAnotherBrokerWhenNotStoreOK=false

infra.rocketmq.producers.pay.namesrvAddr=127.0.0.1:9876
infra.rocketmq.producers.pay.producerGroup=pay_producer_group
```

### 多 Consumer 配置

```properties
infra.rocketmq.consumers.core.namesrvAddr=127.0.0.1:9876
infra.rocketmq.consumers.core.consumerGroup=core_consumer_group
infra.rocketmq.consumers.core.instanceName=core-consumer
infra.rocketmq.consumers.core.messageModel=CLUSTERING
infra.rocketmq.consumers.core.consumeFromWhere=CONSUME_FROM_LAST_OFFSET
infra.rocketmq.consumers.core.consumeThreadMin=20
infra.rocketmq.consumers.core.consumeThreadMax=20
infra.rocketmq.consumers.core.pullBatchSize=32
infra.rocketmq.consumers.core.consumeMessageBatchMaxSize=1
infra.rocketmq.consumers.core.maxReconsumeTimes=-1
infra.rocketmq.consumers.core.consumeTimeout=15
infra.rocketmq.consumers.core.suspendCurrentQueueTimeMillis=1000
infra.rocketmq.consumers.core.awaitTerminationMillisWhenShutdown=1000
infra.rocketmq.consumers.core.accessChannel=
infra.rocketmq.consumers.core.enableMsgTrace=false
infra.rocketmq.consumers.core.customizedTraceTopic=

infra.rocketmq.consumers.marketing.namesrvAddr=127.0.0.1:9876
infra.rocketmq.consumers.marketing.consumerGroup=marketing_consumer_group
```

## 默认值

未显式配置时，以下属性会使用默认值：

- `enabled=false`
- `primaryProducer=""`
- `primaryConsumer=""`
- `instanceName=DEFAULT`
- `vipChannelEnabled=false`
- `useTLS=false`
- `accessChannel=""`
- `enableMsgTrace=false`
- `customizedTraceTopic=""`
- `messageModel=CLUSTERING`
- `consumeFromWhere=CONSUME_FROM_LAST_OFFSET`
- `consumeThreadMin=20`
- `consumeThreadMax=20`
- `pullBatchSize=32`
- `consumeMessageBatchMaxSize=1`
- `maxReconsumeTimes=-1`
- `consumeTimeout=15`
- `suspendCurrentQueueTimeMillis=1000`
- `awaitTerminationMillisWhenShutdown=1000`
- `sendMsgTimeout=3000`
- `compressMsgBodyOverHowmuch=4096`
- `maxMessageSize=4194304`
- `retryTimesWhenSendFailed=2`
- `retryTimesWhenSendAsyncFailed=2`
- `retryAnotherBrokerWhenNotStoreOK=false`

## 自动注册的 Bean

### Producer Bean

每个 Producer 会注册成：

```text
配置名 + RocketMQProducer
```

例如：

- `orderRocketMQProducer`
- `payRocketMQProducer`

### ConsumerFactory Bean

每个 Consumer 会注册成：

```text
配置名 + RocketMQConsumerFactory
```

例如：

- `coreRocketMQConsumerFactory`
- `marketingRocketMQConsumerFactory`

## 使用方式

### 1. 直接注入默认 Producer

如果配置了 `primaryProducer`，可以直接按类型注入：

```java
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Service;

@Service
public class OrderProducerService {
    private final DefaultMQProducer defaultMQProducer;

    public OrderProducerService(DefaultMQProducer defaultMQProducer) {
        this.defaultMQProducer = defaultMQProducer;
    }

    public void send() throws Exception {
        Message message = new Message("order-topic", "hello order".getBytes());
        defaultMQProducer.send(message);
    }
}
```

### 2. 按 Bean 名注入指定 Producer

```java
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MultiProducerService {
    private final DefaultMQProducer orderProducer;
    private final DefaultMQProducer payProducer;

    public MultiProducerService(@Qualifier("orderRocketMQProducer") DefaultMQProducer orderProducer,
                                @Qualifier("payRocketMQProducer") DefaultMQProducer payProducer) {
        this.orderProducer = orderProducer;
        this.payProducer = payProducer;
    }
}
```

### 3. 通过 RocketMQManager 动态选择 Producer

```java
import io.infra.structure.rocketmq.core.RocketMQManager;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.stereotype.Service;

@Service
public class DynamicProducerService {
    private final RocketMQManager rocketMQManager;

    public DynamicProducerService(RocketMQManager rocketMQManager) {
        this.rocketMQManager = rocketMQManager;
    }

    public DefaultMQProducer getProducer(String name) {
        return rocketMQManager.getProducer(name);
    }
}
```

支持以下写法：

- `rocketMQManager.getProducer("order")`
- `rocketMQManager.getProducer("orderRocketMQProducer")`

### 4. 通过 RocketMQManager 动态创建 Consumer

```java
import io.infra.structure.rocketmq.core.RocketMQConsumerFactory;
import io.infra.structure.rocketmq.core.RocketMQManager;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.springframework.stereotype.Service;

@Service
public class DynamicConsumerService {
    private final RocketMQManager rocketMQManager;

    public DynamicConsumerService(RocketMQManager rocketMQManager) {
        this.rocketMQManager = rocketMQManager;
    }

    public void start() throws Exception {
        RocketMQConsumerFactory factory = rocketMQManager.getConsumerFactory("core");
        factory.createAndStart("order-topic", "*", (MessageListenerConcurrently) (msgs, context) -> {
            msgs.forEach(msg -> System.out.println(new String(msg.getBody())));
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
    }
}
```

支持以下写法：

- `rocketMQManager.getConsumerFactory("core")`
- `rocketMQManager.getConsumerFactory("coreRocketMQConsumerFactory")`

### 5. 使用 `@RocketMQMessageListener`

如果你希望使用 RocketMQ 官方注解写法，可以直接在监听器类上标注 `@RocketMQMessageListener`。

如果存在多套 consumer 配置，额外使用 `@InfraRocketMQConsumer` 指定绑定哪套 `infra.rocketmq.consumers` 配置。
如果不写 `@InfraRocketMQConsumer`，则默认使用 `primaryConsumer`。

```java
import io.infra.structure.rocketmq.annotation.InfraRocketMQConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@InfraRocketMQConsumer("core")
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order_consumer_group",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderListener implements RocketMQListener<String> {
    @Override
    public void onMessage(String message) {
        System.out.println(message);
    }
}
```

也可以直接消费 `MessageExt`：

```java
import io.infra.structure.rocketmq.annotation.InfraRocketMQConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@InfraRocketMQConsumer("marketing")
@RocketMQMessageListener(
        topic = "marketing-topic",
        consumerGroup = "marketing_consumer_group"
)
public class MarketingListener implements RocketMQListener<MessageExt> {
    @Override
    public void onMessage(MessageExt message) {
        System.out.println(new String(message.getBody()));
    }
}
```

当前注解模式支持的消费参数包括：

- `topic`
- `consumerGroup`
- `selectorType`
- `selectorExpression`
- `consumeMode`
- `messageModel`
- `consumeThreadNumber`
- `consumeThreadMax`
- `maxReconsumeTimes`
- `consumeTimeout`
- `tlsEnable`
- `namespace`
- `nameServer`
- `accessChannel`
- `enableMsgTrace`
- `customizedTraceTopic`
- `delayLevelWhenNextConsume`
- `suspendCurrentQueueTimeMillis`
- `awaitTerminationMillisWhenShutdown`

当前注解模式暂不支持：

- `RocketMQReplyListener`
- `accessKey / secretKey` ACL 构造器

## ConsumerFactory 能力

`RocketMQConsumerFactory` 提供以下方法：

- `createConsumer()`
- `createAndStart(String topic, String subExpression, MessageListenerConcurrently listener)`
- `createAndStart(String topic, String subExpression, MessageListenerOrderly listener)`
- `createAndStart(String topic, MessageSelector selector, MessageListenerConcurrently listener)`
- `createAndStart(String topic, MessageSelector selector, MessageListenerOrderly listener)`

适合在引用模块时，按业务场景动态创建和启动 Consumer。

## 接入建议

- 如果业务只使用一个 Producer，配置 `primaryProducer` 后直接按类型注入 `DefaultMQProducer`
- 如果业务需要多个 Producer，优先使用 `RocketMQManager`
- 如果业务需要运行时决定消费哪套 RocketMQ 配置，使用 `RocketMQManager.getConsumerFactory(...)`
- `ConsumerFactory` 创建出来的 Consumer 会在 Spring 销毁时统一关闭

## 相关类

- `io.infra.structure.rocketmq.autoconfiguration.InfraRocketMQAutoConfiguration`
- `io.infra.structure.rocketmq.properties.RocketMQConfig`
- `io.infra.structure.rocketmq.definition.RocketMQBeanDefinitionRegistry`
- `io.infra.structure.rocketmq.core.RocketMQManager`
- `io.infra.structure.rocketmq.core.RocketMQConsumerFactory`
