package io.infra.structure.rocketmq.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author codex
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RocketMQConsumerProperties extends RocketMQBaseProperties {
    /**
     * consumer group
     */
    private String consumerGroup = "";

    /**
     * CLUSTERING / BROADCASTING
     */
    private String messageModel = "CLUSTERING";

    /**
     * CONSUME_FROM_LAST_OFFSET / CONSUME_FROM_FIRST_OFFSET / CONSUME_FROM_TIMESTAMP
     */
    private String consumeFromWhere = "CONSUME_FROM_LAST_OFFSET";

    /**
     * 消费线程最小值
     */
    private Integer consumeThreadMin = 20;

    /**
     * 消费线程最大值
     */
    private Integer consumeThreadMax = 20;

    /**
     * 拉取消息批次大小
     */
    private Integer pullBatchSize = 32;

    /**
     * 单次消费消息数量
     */
    private Integer consumeMessageBatchMaxSize = 1;

    /**
     * 最大重试次数
     */
    private Integer maxReconsumeTimes = -1;

    /**
     * 消费超时时间，单位分钟
     */
    private Long consumeTimeout = 15L;

    /**
     * 当前队列挂起时间，单位毫秒
     */
    private Long suspendCurrentQueueTimeMillis = 1000L;

    /**
     * 关闭等待时间，单位毫秒
     */
    private Long awaitTerminationMillisWhenShutdown = 1000L;
}
