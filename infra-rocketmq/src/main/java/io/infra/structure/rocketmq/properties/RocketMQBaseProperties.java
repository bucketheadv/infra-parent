package io.infra.structure.rocketmq.properties;

import lombok.Data;

/**
 * @author codex
 */
@Data
public class RocketMQBaseProperties {
    /**
     * NameServer 地址，多个地址使用分号分隔
     */
    private String namesrvAddr = "";

    /**
     * 命名空间
     */
    private String namespace = "";

    /**
     * 客户端实例名
     */
    private String instanceName = "DEFAULT";

    /**
     * 是否启用 VIP 通道
     */
    private Boolean vipChannelEnabled = Boolean.FALSE;

    /**
     * 是否启用 TLS
     */
    private Boolean useTLS = Boolean.FALSE;

    /**
     * 访问通道
     */
    private String accessChannel = "";

    /**
     * 是否启用消息轨迹
     */
    private Boolean enableMsgTrace = Boolean.FALSE;

    /**
     * 自定义轨迹 topic
     */
    private String customizedTraceTopic = "";
}
