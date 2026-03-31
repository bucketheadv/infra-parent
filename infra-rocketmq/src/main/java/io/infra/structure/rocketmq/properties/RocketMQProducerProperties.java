package io.infra.structure.rocketmq.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author codex
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RocketMQProducerProperties extends RocketMQBaseProperties {
    /**
     * producer group
     */
    private String producerGroup = "";

    /**
     * 发送超时时间，单位毫秒
     */
    private Integer sendMsgTimeout = 3000;

    /**
     * 消息体压缩阈值
     */
    private Integer compressMsgBodyOverHowmuch = 4096;

    /**
     * 最大消息大小
     */
    private Integer maxMessageSize = 4 * 1024 * 1024;

    /**
     * 同步发送失败重试次数
     */
    private Integer retryTimesWhenSendFailed = 2;

    /**
     * 异步发送失败重试次数
     */
    private Integer retryTimesWhenSendAsyncFailed = 2;

    /**
     * 存储失败时是否重试其他 broker
     */
    private Boolean retryAnotherBrokerWhenNotStoreOK = Boolean.FALSE;
}
