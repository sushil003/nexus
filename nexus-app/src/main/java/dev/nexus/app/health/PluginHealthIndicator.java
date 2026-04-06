package dev.nexus.app.health;

import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PluginHealthIndicator implements HealthIndicator {

    private final PluginRegistry pluginRegistry;

    public PluginHealthIndicator(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public Health health() {
        Map<String, NexusPlugin> plugins = pluginRegistry.getAll();

        if (plugins.isEmpty()) {
            return Health.down().withDetail("reason", "No plugins registered").build();
        }

        Map<String, Object> details = new HashMap<>();
        details.put("pluginCount", plugins.size());

        Map<String, String> pluginDetails = new HashMap<>();
        for (var entry : plugins.entrySet()) {
            NexusPlugin plugin = entry.getValue();
            pluginDetails.put(entry.getKey(), plugin.getDefaultAuthType().name());
        }
        details.put("plugins", pluginDetails);

        return Health.up().withDetails(details).build();
    }
}
