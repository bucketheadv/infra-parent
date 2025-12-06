package io.infra.structure.db.filter;

import com.google.common.collect.Sets;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

import java.util.Set;

/**
 * 排除 MyBatis-Flex 中有问题的自动配置类
 * @author sven
 * Created on 2025/12/6
 */
public class MyBatisFlexAutoConfigurationFilter implements AutoConfigurationImportFilter {
    private static final Set<String> SKIP_SET = Sets.newHashSet(
            "com.mybatisflex.spring.boot.FlexTransactionAutoConfiguration"
    );

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            matches[i] = !SKIP_SET.contains(autoConfigurationClasses[i]);
        }
        return matches;
    }
}

