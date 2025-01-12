package org.infra.structure.redis.configuration;

import lombok.extern.slf4j.Slf4j;
import org.infra.structure.redis.constants.Const;
import org.infra.structure.redis.properties.RedisConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * @author sven
 * Created on 2022/1/14 10:25 下午
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = Const.configPrefix, value = "enabled", havingValue = "true")
public class InfraRedisAutoConfiguration {
    @Bean
    public static RedisConfig redisConfig(Environment env) {
        RedisConfig redisConfig = null;
        try {
            redisConfig = Binder.get(env).bind(Const.configPrefix, RedisConfig.class).get();
        } catch (Exception e) {
            log.error("绑定Redis配置失败: ", e);
        }
        return redisConfig;
    }

    @Bean
    @ConditionalOnMissingBean
    public static RedisDefinitionRegistry redisDefinitionRegistry(RedisConfig redisConfig) {
        return new RedisDefinitionRegistry(redisConfig);
    }
}
