package org.infra.structure.core.startup;

import lombok.extern.slf4j.Slf4j;
import org.infra.structure.core.context.ApplicationContextHolder;
import org.infra.structure.core.tool.BinderTool;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;
import java.util.Set;

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

        Set<String> includes = startupProperties.getIncludes();
        for (String whitelist : includes) {
            if (className.matches(whitelist)) {
                return false;
            }
        }

        Set<String> excludes = startupProperties.getExcludes();
        for (String pattern : excludes) {
            if (className.matches(pattern)) {
                return true;
            }
        }
        return false;
    }
}
