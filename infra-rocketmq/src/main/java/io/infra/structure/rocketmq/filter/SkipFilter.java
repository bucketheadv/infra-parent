package io.infra.structure.rocketmq.filter;

import com.google.common.collect.Sets;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

import java.util.Set;

/**
 * 禁用 RocketMQ 官方自动配置，避免与 infra-rocketmq 的多实例实现冲突。
 *
 * @author codex
 */
public class SkipFilter implements AutoConfigurationImportFilter {
    private static final Set<String> SKIP_SET = Sets.newHashSet(
            "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration"
    );

    @Override
    public boolean @NonNull [] match(String[] autoConfigurationClasses, @NonNull AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            matches[i] = !SKIP_SET.contains(autoConfigurationClasses[i]);
        }
        return matches;
    }
}
