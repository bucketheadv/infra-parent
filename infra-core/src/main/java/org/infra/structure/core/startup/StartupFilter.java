package org.infra.structure.core.startup;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.infra.structure.core.context.ApplicationContextHolder;
import org.infra.structure.core.tool.BinderTool;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;

/**
 * @author sven
 * Created on 2025/1/12 14:49
 */
@Slf4j
public class StartupFilter extends TypeExcludeFilter {

    private final StartupProperties startupProperties;

    public StartupFilter() {
        Environment env = ApplicationContextHolder.getApplicationContext().getEnvironment();
        startupProperties = BinderTool.bind(env, StartupProperties.AUTOLOAD_PREFIX, StartupProperties.class);
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        String className = metadataReader.getClassMetadata().getClassName();
        if (startupProperties == null) {
            return false;
        }
        if (CollectionUtils.isNotEmpty(startupProperties.getIncludes())) {
            for (String whitelist : startupProperties.getIncludes()) {
                if (className.matches(whitelist)) {
                    return false;
                }
            }
        }
        if (CollectionUtils.isNotEmpty(startupProperties.getExcludes())) {
            for (String pattern : startupProperties.getExcludes()) {
                if (className.matches(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }
}
