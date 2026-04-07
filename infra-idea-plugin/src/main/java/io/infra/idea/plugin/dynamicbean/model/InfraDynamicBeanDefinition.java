package io.infra.idea.plugin.dynamicbean.model;

import com.intellij.lang.properties.IProperty;

/**
 * Dynamic bean discovered from a project properties file.
 */
public class InfraDynamicBeanDefinition {
    private final String beanName;
    private final String configName;
    private final InfraDynamicBeanKind kind;
    private final IProperty navigationProperty;
    private final boolean primary;

    public InfraDynamicBeanDefinition(String beanName,
                                      String configName,
                                      InfraDynamicBeanKind kind,
                                      IProperty navigationProperty,
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

    public IProperty getNavigationProperty() {
        return navigationProperty;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean matchesType(String typeName) {
        return kind.supportsType(typeName);
    }
}
