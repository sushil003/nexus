package dev.nexus.core.inspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import dev.nexus.core.plugin.EndpointMeta;
import dev.nexus.core.plugin.EndpointSchemas;
import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InspectService {

    private final PluginRegistry pluginRegistry;
    private final SchemaGenerator schemaGenerator;

    public InspectService(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;

        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(Option.EXTRA_OPEN_API_FORMAT_VALUES);
        this.schemaGenerator = new SchemaGenerator(configBuilder.build());
    }

    public List<EndpointInfo> listOperations(String pluginFilter) {
        List<EndpointInfo> operations = new ArrayList<>();

        Map<String, NexusPlugin> plugins = pluginFilter != null
                ? pluginRegistry.get(pluginFilter).map(p -> Map.of(pluginFilter, p)).orElse(Map.of())
                : pluginRegistry.getAll();

        for (var entry : plugins.entrySet()) {
            NexusPlugin plugin = entry.getValue();
            Map<String, EndpointMeta> metaMap = plugin.getEndpointMeta();

            for (var metaEntry : metaMap.entrySet()) {
                EndpointMeta meta = metaEntry.getValue();
                operations.add(new EndpointInfo(
                        metaEntry.getKey(),
                        meta.description(),
                        meta.riskLevel().name(),
                        meta.irreversible()));
            }
        }

        return operations;
    }

    public Optional<SchemaInfo> getSchema(String path) {
        String[] segments = path.split("\\.", 2);
        if (segments.length < 2) return Optional.empty();

        String pluginId = segments[0];
        return pluginRegistry.get(pluginId).flatMap(plugin -> {
            Map<String, EndpointSchemas> schemas = plugin.getEndpointSchemas();
            EndpointSchemas endpointSchemas = schemas.get(path);
            if (endpointSchemas == null) return Optional.empty();

            Map<String, EndpointMeta> metaMap = plugin.getEndpointMeta();
            EndpointMeta meta = metaMap.get(path);

            return Optional.of(new SchemaInfo(
                    path,
                    meta != null ? meta.description() : "",
                    endpointSchemas.inputSchema(),
                    endpointSchemas.outputSchema()));
        });
    }

    public JsonNode generateSchema(Class<?> type) {
        return schemaGenerator.generateSchema(type);
    }

    public record EndpointInfo(String path, String description, String riskLevel, boolean irreversible) {}

    public record SchemaInfo(String path, String description, JsonNode inputSchema, JsonNode outputSchema) {}
}
