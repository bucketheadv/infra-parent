package io.infra.idea.plugin.dynamicbean.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory view of all supported dynamic beans inside a project.
 */
public class InfraDynamicBeanModel {
    private final Map<String, InfraDynamicBeanDefinition> beansByName = new LinkedHashMap<>();
    private final Map<InfraDynamicBeanKind, InfraDynamicBeanDefinition> primaryBeans = new LinkedHashMap<>();
    private final Map<InfraDynamicBeanKind, InfraDynamicBeanDefinition> firstBeans = new LinkedHashMap<>();

    public void add(InfraDynamicBeanDefinition definition) {
        beansByName.put(definition.getBeanName(), definition);
        firstBeans.putIfAbsent(definition.getKind(), definition);
        if (definition.isPrimary()) {
            primaryBeans.put(definition.getKind(), definition);
        }
    }

    public Collection<InfraDynamicBeanDefinition> getAllBeans() {
        return beansByName.values();
    }

    public InfraDynamicBeanDefinition findByName(String beanName) {
        return beansByName.get(beanName);
    }

    public InfraDynamicBeanDefinition findPrimary(InfraDynamicBeanKind kind) {
        InfraDynamicBeanDefinition primary = primaryBeans.get(kind);
        return primary != null ? primary : firstBeans.get(kind);
    }
}
