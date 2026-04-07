package io.infra.idea.plugin.dynamicbean.spring;

import com.intellij.openapi.module.Module;
import com.intellij.spring.model.SpringImplicitBean;
import com.intellij.spring.model.SpringImplicitBeansProviderBase;
import io.infra.idea.plugin.dynamicbean.index.InfraDynamicBeanIndex;
import io.infra.idea.plugin.dynamicbean.model.InfraDynamicBeanDefinition;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Exposes infra dynamic beans to IntelliJ's Spring model so autowiring checks can resolve them.
 */
public class InfraDynamicBeanSpringImplicitBeansProvider extends SpringImplicitBeansProviderBase {
    private static final String PROVIDER_NAME = "Infra Dynamic Bean";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected Collection getImplicitBeans(@NonNull Module module) {
        List<SpringImplicitBean> beans = new ArrayList<>();
        for (InfraDynamicBeanDefinition definition : InfraDynamicBeanIndex.getBeans(module.getProject(), null)) {
            addBeanIfResolvable(beans, module, definition);
        }
        return beans;
    }

    private void addBeanIfResolvable(List<SpringImplicitBean> beans, Module module, InfraDynamicBeanDefinition definition) {
        for (String qualifiedTypeName : definition.getKind().getQualifiedTypeNames()) {
            if (findClassInDependenciesAndLibraries(module, qualifiedTypeName) != null) {
                beans.add(SpringImplicitBean.create(module, definition.getBeanName(), qualifiedTypeName, PROVIDER_NAME));
                return;
            }
        }
    }
}
