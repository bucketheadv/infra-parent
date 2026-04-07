package io.infra.idea.plugin.dynamicbean.inspection;

import org.jetbrains.annotations.NotNull;

/**
 * Kotlin registration uses a distinct short name to avoid duplicate inspection IDs.
 */
public class InfraDynamicBeanKotlinInspection extends InfraDynamicBeanInspection {
    @Override
    public @NotNull String getDisplayName() {
        return "Infra Dynamic Bean injection check";
    }

    @Override
    public @NotNull String getShortName() {
        return "InfraDynamicBeanKotlin";
    }
}
