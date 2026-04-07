package io.infra.idea.plugin.dynamicbean.model;

/**
 * Dynamic bean discovered from a project properties file.
 */
public class InfraDynamicBeanDefinition {
    private final String beanName;
    private final String configName;
    private final InfraDynamicBeanKind kind;
    private final InfraDynamicBeanConfigProperty navigationProperty;
    private final boolean primary;

    public InfraDynamicBeanDefinition(String beanName,
                                      String configName,
                                      InfraDynamicBeanKind kind,
                                      InfraDynamicBeanConfigProperty navigationProperty,
                                      boolean primary) {
        this.beanName = beanName;
        this.configName = configName;
        this.kind = kind;
        this.navigationProperty = navigationProperty;
        this.primary = primary;
    }

    public String getBeanName() {
        return beanName;
    }

    public String getConfigName() {
        return configName;
    }

    public InfraDynamicBeanKind getKind() {
        return kind;
    }

    public InfraDynamicBeanConfigProperty getNavigationProperty() {
        return navigationProperty;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean matchesType(String typeName) {
        return kind.supportsType(typeName);
    }

    public boolean matchesPropertyKey(String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return false;
        }
        String configPrefix = switch (kind) {
            case REDIS_TEMPLATE, REDIS_CLUSTER_TEMPLATE -> "infra.redis.template.";
            case ROCKETMQ_PRODUCER -> "infra.rocketmq.producers.";
            case ROCKETMQ_CONSUMER_FACTORY -> "infra.rocketmq.consumers.";
        };
        String beanKey = configPrefix + configName;
        return propertyKey.equals(beanKey) || propertyKey.startsWith(beanKey + ".");
    }
}
