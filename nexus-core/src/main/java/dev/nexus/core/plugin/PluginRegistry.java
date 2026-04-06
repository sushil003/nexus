package dev.nexus.core.plugin;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PluginRegistry {

    private final Map<String, NexusPlugin> plugins;

    public PluginRegistry(List<NexusPlugin> pluginList) {
        this.plugins = pluginList.stream()
                .collect(Collectors.toMap(NexusPlugin::getId, Function.identity()));
    }

    public Optional<NexusPlugin> get(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    public Map<String, NexusPlugin> getAll() {
        return Map.copyOf(plugins);
    }

    public List<String> getPluginIds() {
        return List.copyOf(plugins.keySet());
    }
}
