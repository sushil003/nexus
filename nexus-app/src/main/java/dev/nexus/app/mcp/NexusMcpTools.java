package dev.nexus.app.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nexus.core.auth.KeyManager;
import dev.nexus.core.auth.KeyManagerFactory;
import dev.nexus.core.endpoint.EndpointExecutor;
import dev.nexus.core.inspect.InspectService;
import dev.nexus.core.inspect.JsonSchemaToTypeString;
import dev.nexus.core.plugin.AuthType;
import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class NexusMcpTools {

    private final PluginRegistry pluginRegistry;
    private final KeyManagerFactory keyManagerFactory;
    private final EndpointExecutor endpointExecutor;
    private final InspectService inspectService;
    private final ObjectMapper objectMapper;

    public NexusMcpTools(PluginRegistry pluginRegistry,
                         KeyManagerFactory keyManagerFactory,
                         EndpointExecutor endpointExecutor,
                         InspectService inspectService,
                         ObjectMapper objectMapper) {
        this.pluginRegistry = pluginRegistry;
        this.keyManagerFactory = keyManagerFactory;
        this.endpointExecutor = endpointExecutor;
        this.inspectService = inspectService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Check auth status for all plugins, show setup instructions")
    public String nexus_setup(
            @ToolParam(description = "Optional: plugin ID to check specific plugin", required = false) String plugin) {

        Map<String, NexusPlugin> plugins = plugin != null
                ? pluginRegistry.get(plugin).map(p -> Map.of(plugin, p)).orElse(Map.of())
                : pluginRegistry.getAll();

        if (plugins.isEmpty()) {
            return plugin != null ? "Unknown plugin: " + plugin : "No plugins registered.";
        }

        StringBuilder sb = new StringBuilder("# Nexus Plugin Status\n\n");

        for (var entry : plugins.entrySet()) {
            String id = entry.getKey();
            NexusPlugin p = entry.getValue();
            sb.append("## ").append(id).append("\n");
            sb.append("- Auth type: ").append(p.getDefaultAuthType()).append("\n");
            sb.append("- Required fields: ").append(p.getAuthConfig().requiredFields()).append("\n");

            boolean configured = isPluginConfigured(id, p);
            sb.append("- Status: ").append(configured ? "✓ Configured" : "✗ Not configured").append("\n");

            if (!configured) {
                if (p.getDefaultAuthType() == AuthType.OAUTH2) {
                    sb.append("- Setup: POST /api/credentials/").append(id)
                      .append(" with client_id and client_secret, then GET /oauth/").append(id).append("/start\n");
                } else {
                    sb.append("- Setup: POST /api/credentials/").append(id)
                      .append(" with ").append(String.join(", ", p.getAuthConfig().requiredFields())).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Tool(description = "List available endpoint paths with descriptions and risk levels")
    public String list_operations(
            @ToolParam(description = "Optional: plugin ID filter", required = false) String plugin) {

        List<InspectService.EndpointInfo> operations = inspectService.listOperations(plugin);

        if (operations.isEmpty()) {
            return plugin != null ? "No operations found for plugin: " + plugin : "No operations available.";
        }

        StringBuilder sb = new StringBuilder("# Available Operations\n\n");
        sb.append("| Path | Description | Risk | Irreversible |\n");
        sb.append("|------|-------------|------|--------------|\n");

        for (InspectService.EndpointInfo op : operations) {
            sb.append("| ").append(op.path())
              .append(" | ").append(op.description())
              .append(" | ").append(op.riskLevel())
              .append(" | ").append(op.irreversible() ? "Yes" : "No")
              .append(" |\n");
        }

        return sb.toString();
    }

    @Tool(description = "Get input/output schema for a specific endpoint")
    public String get_schema(
            @ToolParam(description = "Dot-path, e.g. github.issues.create") String path) {

        Optional<InspectService.SchemaInfo> schemaOpt = inspectService.getSchema(path);

        if (schemaOpt.isEmpty()) {
            return "No schema found for: " + path;
        }

        InspectService.SchemaInfo schema = schemaOpt.get();
        StringBuilder sb = new StringBuilder("# Schema: ").append(path).append("\n\n");
        sb.append("**Description:** ").append(schema.description()).append("\n\n");

        if (schema.inputSchema() != null) {
            sb.append("## Input\n```\n");
            sb.append(JsonSchemaToTypeString.convert(schema.inputSchema()));
            sb.append("\n```\n\n");
            sb.append("**Raw JSON Schema:**\n```json\n");
            sb.append(schema.inputSchema().toPrettyString());
            sb.append("\n```\n\n");
        }

        if (schema.outputSchema() != null) {
            sb.append("## Output\n```\n");
            sb.append(JsonSchemaToTypeString.convert(schema.outputSchema()));
            sb.append("\n```\n");
        }

        return sb.toString();
    }

    @Tool(description = "Execute an endpoint by path with JSON arguments")
    public String nexus_run(
            @ToolParam(description = "Endpoint path, e.g. github.repos.list") String path,
            @ToolParam(description = "JSON arguments (optional)", required = false) String args) {

        try {
            Object parsedArgs = null;
            if (args != null && !args.isBlank()) {
                parsedArgs = objectMapper.readValue(args, Map.class);
            }

            Object result = endpointExecutor.execute(path, parsedArgs, "default");

            if (result instanceof String s) {
                return s;
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "Error parsing arguments: " + e.getMessage();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private boolean isPluginConfigured(String pluginId, NexusPlugin plugin) {
        if (plugin.getDefaultAuthType() == AuthType.NONE) {
            return true;
        }
        try {
            KeyManager keyManager = keyManagerFactory.createForPlugin(pluginId, "default");
            List<String> requiredFields = plugin.getAuthConfig().requiredFields();
            if (plugin.getDefaultAuthType() == AuthType.OAUTH2) {
                return keyManager.getField("access_token").isPresent();
            }
            return requiredFields.stream().allMatch(f -> keyManager.getField(f).isPresent());
        } catch (Exception e) {
            return false;
        }
    }
}
