package io.infra.structure.redis.definition;

import io.infra.structure.redis.cluster.core.JedisClusterTemplate;
import io.infra.structure.redis.properties.RedisClusterNodeProperties;
import io.infra.structure.redis.properties.RedisConfig;
import io.infra.structure.redis.properties.RedisProperties;
import io.infra.structure.redis.properties.RedisTemplateConfig;
import io.infra.structure.redis.replication.core.DefaultJedisTemplate;
import io.infra.structure.redis.replication.core.JedisTemplate;
import io.infra.structure.redis.replication.core.RedisEndpoint;
import io.infra.structure.redis.replication.core.RedisNodeGroup;
import io.infra.structure.redis.replication.core.RedisTemplateTopology;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * @author sven
 * Created on 2022/1/14 10:51 下午
 */
@Slf4j
public class RedisBeanDefinitionRegistry implements BeanDefinitionRegistryPostProcessor {
    private final RedisConfig redisConfig;

    public RedisBeanDefinitionRegistry(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    private JedisPool buildJedisPool(RedisProperties properties) {
        HostAndPort hostAndPort = new HostAndPort(properties.getHost(), properties.getPort());
        return new JedisPool(buildJedisPoolConfig(properties), hostAndPort, buildClientConfig(properties));
    }

    private JedisPoolConfig buildJedisPoolConfig(RedisProperties properties) {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        applyPoolConfig(jedisPoolConfig, properties);
        return jedisPoolConfig;
    }

    private DefaultJedisClientConfig buildClientConfig(RedisProperties properties) {
        return DefaultJedisClientConfig.builder()
                .password(properties.getPassword())
                .database(properties.getDb())
                .clientName(properties.getClientName())
                .connectionTimeoutMillis(properties.getConnectionTimeoutMillis())
                .socketTimeoutMillis(properties.getSocketTimeoutMillis())
                .blockingSocketTimeoutMillis(properties.getBlockingSocketTimeoutMillis())
                .build();
    }

    private GenericObjectPoolConfig<Connection> buildClusterPoolConfig(RedisProperties properties) {
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        applyPoolConfig(poolConfig, properties);
        return poolConfig;
    }

    private void applyPoolConfig(GenericObjectPoolConfig<?> poolConfig, RedisProperties properties) {
        poolConfig.setTestOnReturn(properties.isTestOnReturn());
        poolConfig.setTestOnBorrow(properties.isTestOnBorrow());
        poolConfig.setTestOnCreate(properties.isTestOnCreate());
        poolConfig.setTestWhileIdle(properties.isTestWhileIdle());
        poolConfig.setJmxEnabled(properties.isJmxEnabled());
        poolConfig.setJmxNameBase(properties.getJmxNameBase());
        poolConfig.setJmxNamePrefix(properties.getJmxNamePrefix());
        poolConfig.setMaxIdle(properties.getMaxIdle());
        poolConfig.setMaxTotal(properties.getMaxTotal());
        poolConfig.setMinIdle(properties.getMinIdle());
    }

    private RedisTemplateTopology buildTopology(RedisTemplateConfig config) {
        RedisEndpoint master = new RedisEndpoint(
                "default",
                config.getMaster().getHost() + ":" + config.getMaster().getPort(),
                true,
                buildJedisPool(config.getMaster())
        );
        List<RedisEndpoint> slaves = new ArrayList<>();
        for (RedisProperties slave : config.getSlaves()) {
            slaves.add(new RedisEndpoint(
                    "default",
                    slave.getHost() + ":" + slave.getPort(),
                    false,
                    buildJedisPool(slave)
            ));
        }
        return new RedisTemplateTopology(
                config.getLoadBalance(),
                config.getReadPreference(),
                config.getMaxAttempts(),
                config.getRetryIntervalMillis(),
                config.getFailoverCooldownMillis(),
                config.isProbeOnRecover(),
                List.of(new RedisNodeGroup("default", master, slaves))
        );
    }

    private Set<HostAndPort> buildClusterNodes(RedisTemplateConfig config) {
        Set<HostAndPort> clusterNodes = new LinkedHashSet<>();
        for (RedisClusterNodeProperties node : config.getCluster().getNodes()) {
            clusterNodes.add(new HostAndPort(node.getHost(), node.getPort()));
        }
        if (clusterNodes.isEmpty()) {
            throw new IllegalStateException("Redis cluster template must configure at least one cluster node");
        }
        return clusterNodes;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        Map<String, RedisTemplateConfig> templates = redisConfig.getTemplate();
        String primaryKey = templates.containsKey(redisConfig.getPrimary()) ? redisConfig.getPrimary() : null;
        for (String k : templates.keySet()) {
            RedisTemplateConfig v = templates.get(k);

            if (StringUtils.isBlank(primaryKey)) {
                primaryKey = k;
            }

            boolean primary = k.equals(primaryKey);
            String key;
            BeanDefinition beanDefinition;
            if (v.getMode() == RedisTemplateConfig.Mode.cluster) {
                key = k + JedisClusterTemplate.class.getSimpleName();
                RedisProperties clusterClientProperties = v.getCluster().getClient();
                beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(JedisClusterTemplate.class)
                        .addConstructorArgValue(key)
                        .addConstructorArgValue(buildClusterNodes(v))
                        .addConstructorArgValue(buildClientConfig(clusterClientProperties))
                        .addConstructorArgValue(v.getCluster().getMaxAttempts())
                        .addConstructorArgValue(buildClusterPoolConfig(clusterClientProperties))
                        .setPrimary(primary)
                        .setDestroyMethodName("close")
                        .getBeanDefinition();
            } else {
                key = k + JedisTemplate.class.getSimpleName();
                beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(DefaultJedisTemplate.class)
                        .addConstructorArgValue(key)
                        .addConstructorArgValue(buildTopology(v))
                        .setPrimary(primary)
                        .setDestroyMethodName("close")
                        .getBeanDefinition();
            }
            beanDefinitionRegistry.registerBeanDefinition(key, beanDefinition);
            log.info("Bean: {} 注册成功", key);
        }
        log.info("加载redis数据源 {} 个, primary 数据源名称为 [{}]", redisConfig.getTemplate().size(), primaryKey);
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }
}
