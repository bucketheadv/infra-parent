package io.infra.structure.rocketmq.properties;

import io.infra.structure.rocketmq.constants.Const;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author codex
 */
@Data
@Configuration
@ConfigurationProperties(prefix = Const.configPrefix)
public class RocketMQConfig {
    /**
     * 是否启用 RocketMQ
     */
    private boolean enabled = false;

    /**
     * 主 producer 名称
     */
    private String primaryProducer = "";

    /**
     * 主 consumer factory 名称
     */
    private String primaryConsumer = "";

    /**
     * 多 producer 配置
     */
    private Map<String, RocketMQProducerProperties> producers = new LinkedHashMap<>();

    /**
     * 多 consumer 配置
     */
    private Map<String, RocketMQConsumerProperties> consumers = new LinkedHashMap<>();
}
