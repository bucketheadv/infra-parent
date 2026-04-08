package io.infra.idea.plugin.icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Icon set used by dynamic bean gutter markers.
 */
public final class InfraDynamicBeanIcons {
    public static final Icon TO_CONFIG = IconLoader.getIcon("/icons/dynamic-bean-to-config.svg", InfraDynamicBeanIcons.class);
    public static final Icon TO_INJECTION = IconLoader.getIcon("/icons/dynamic-bean-to-injection.svg", InfraDynamicBeanIcons.class);

    private InfraDynamicBeanIcons() {
    }
}
