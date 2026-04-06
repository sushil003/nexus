package dev.nexus.core.config;

import dev.nexus.core.permission.PermissionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus")
public record NexusProperties(
        String kek,
        String adminPassword,
        PermissionConfig permission,
        PluginsConfig plugins
) {
    public record PermissionConfig(PermissionMode mode) {
        public PermissionConfig {
            if (mode == null) mode = PermissionMode.CAUTIOUS;
        }
    }

    public record PluginsConfig(ExcalidrawConfig excalidraw) {
        public record ExcalidrawConfig(String directory) {
        }
    }
}
