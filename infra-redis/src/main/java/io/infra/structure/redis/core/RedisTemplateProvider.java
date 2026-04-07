package io.infra.structure.redis.core;

import io.infra.structure.redis.cluster.core.JedisClusterTemplate;
import io.infra.structure.redis.replication.core.JedisTemplate;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * IDEA-friendly access entry for dynamically registered redis templates.
 */
public class RedisTemplateProvider {
    private final ListableBeanFactory beanFactory;

    public RedisTemplateProvider(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public Map<String, JedisTemplate> jedisTemplates() {
        return beanFactory.getBeansOfType(JedisTemplate.class);
    }

    public Map<String, JedisClusterTemplate> jedisClusterTemplates() {
        return beanFactory.getBeansOfType(JedisClusterTemplate.class);
    }

    public JedisTemplate jedisTemplate(String templateName) {
        Assert.hasText(templateName, "templateName must not be blank");
        return getBean(templateName + JedisTemplate.class.getSimpleName(), JedisTemplate.class);
    }

    public JedisClusterTemplate jedisClusterTemplate(String templateName) {
        Assert.hasText(templateName, "templateName must not be blank");
        return getBean(templateName + JedisClusterTemplate.class.getSimpleName(), JedisClusterTemplate.class);
    }

    private <T> T getBean(String beanName, Class<T> beanType) {
        try {
            return beanFactory.getBean(beanName, beanType);
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalArgumentException("Redis template bean not found: " + beanName, e);
        }
    }
}
