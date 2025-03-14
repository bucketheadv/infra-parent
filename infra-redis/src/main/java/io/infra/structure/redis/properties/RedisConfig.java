package io.infra.structure.redis.properties;

import lombok.Data;
import io.infra.structure.redis.constants.Const;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @author sven
 * Created on 2022/1/14 10:35 下午
 */
@Data
@Configuration
@ConfigurationProperties(prefix = Const.configPrefix)
public class RedisConfig {
    /**
     * 是否启用redis
     */
    private boolean enabled = false;

    /**
     * 主redis实例名称
     */
    private String primary;

    /**
     * 配置模板映射
     */
    private Map<String, RedisMasterSlaveConfig> template;
}
