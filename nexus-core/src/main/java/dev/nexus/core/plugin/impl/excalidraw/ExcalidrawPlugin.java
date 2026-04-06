package dev.nexus.core.plugin.impl.excalidraw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nexus.core.config.NexusProperties;
import dev.nexus.core.endpoint.EndpointHandler;
import dev.nexus.core.endpoint.EndpointNode;
import dev.nexus.core.error.ErrorHandler;
import dev.nexus.core.error.NexusException;
import dev.nexus.core.hook.EndpointHooks;
import dev.nexus.core.plugin.*;
import dev.nexus.core.webhook.PluginWebhookMatcher;
import dev.nexus.core.webhook.WebhookDefinition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class ExcalidrawPlugin implements NexusPlugin {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final String directory;

    public ExcalidrawPlugin(NexusProperties properties) {
        this.directory = properties.plugins() != null && properties.plugins().excalidraw() != null
                ? properties.plugins().excalidraw().directory()
                : "./excalidraw-files";
    }

    @Override
    public String getId() { return "excalidraw"; }

    @Override
    public AuthType getDefaultAuthType() { return AuthType.NONE; }

    @Override
    public EndpointNode getEndpoints() {
        return new EndpointNode.Group(Map.of(
                "scenes", new EndpointNode.Group(Map.of(
                        "list", leaf(this::listScenes),
                        "get", leaf(this::getScene),
                        "create", leaf(this::createScene)
                ))
        ));
    }

    @Override
    public Map<String, WebhookDefinition> getWebhooks() { return Map.of(); }

    @Override
    public Optional<PluginWebhookMatcher> getPluginWebhookMatcher() { return Optional.empty(); }

    @Override
    public Map<String, EndpointMeta> getEndpointMeta() {
        return Map.of(
                "excalidraw.scenes.list", new EndpointMeta(RiskLevel.READ, "List .excalidraw files in configured directory", false),
                "excalidraw.scenes.get", new EndpointMeta(RiskLevel.READ, "Read and return scene elements JSON", false),
                "excalidraw.scenes.create", new EndpointMeta(RiskLevel.WRITE, "Create a new .excalidraw JSON file", false)
        );
    }

    @Override
    public Map<String, EndpointSchemas> getEndpointSchemas() {
        ObjectNode emptyInput = mapper.createObjectNode().put("type", "object");

        ObjectNode getInput = mapper.createObjectNode().put("type", "object");
        getInput.putObject("properties").putObject("name").put("type", "string");
        getInput.putArray("required").add("name");

        ObjectNode createInput = mapper.createObjectNode().put("type", "object");
        ObjectNode createProps = createInput.putObject("properties");
        createProps.putObject("name").put("type", "string");
        createProps.putObject("elements").put("type", "array");
        createInput.putArray("required").add("name").add("elements");

        ObjectNode arrayOutput = mapper.createObjectNode().put("type", "array");
        ObjectNode objectOutput = mapper.createObjectNode().put("type", "object");

        return Map.of(
                "excalidraw.scenes.list", new EndpointSchemas(emptyInput, arrayOutput),
                "excalidraw.scenes.get", new EndpointSchemas(getInput, objectOutput),
                "excalidraw.scenes.create", new EndpointSchemas(createInput, objectOutput)
        );
    }

    @Override
    public PluginAuthConfig getAuthConfig() {
        return new PluginAuthConfig(List.of());
    }

    @Override
    public Optional<OAuthConfig> getOAuthConfig() { return Optional.empty(); }

    @Override
    public Optional<ErrorHandler> getErrorHandler() { return Optional.empty(); }

    // --- Endpoint handlers ---

    private Object listScenes(dev.nexus.core.context.PluginContext ctx, Object args) {
        Path dir = Path.of(directory);
        ensureDirectoryExists(dir);

        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".excalidraw"))
                    .map(p -> Map.of(
                            "name", p.getFileName().toString().replace(".excalidraw", ""),
                            "path", p.toString(),
                            "size", getFileSize(p)
                    ))
                    .toList();
        } catch (IOException e) {
            throw new NexusException("Failed to list scenes: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object getScene(dev.nexus.core.context.PluginContext ctx, Object args) {
        Map<String, Object> params = (Map<String, Object>) args;
        String name = (String) params.get("name");
        Path file = Path.of(directory, name + ".excalidraw");

        if (!Files.exists(file)) {
            throw new NexusException("Scene not found: " + name);
        }

        try {
            String content = Files.readString(file);
            return mapper.readValue(content, Map.class);
        } catch (IOException e) {
            throw new NexusException("Failed to read scene: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object createScene(dev.nexus.core.context.PluginContext ctx, Object args) {
        Map<String, Object> params = (Map<String, Object>) args;
        String name = (String) params.get("name");
        Object elements = params.get("elements");
        Path dir = Path.of(directory);
        ensureDirectoryExists(dir);
        Path file = dir.resolve(name + ".excalidraw");

        Map<String, Object> scene = Map.of(
                "type", "excalidraw",
                "version", 2,
                "elements", elements,
                "appState", Map.of("viewBackgroundColor", "#ffffff"),
                "files", Map.of()
        );

        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(scene);
            Files.writeString(file, json);
            return Map.of("status", "created", "name", name, "path", file.toString());
        } catch (IOException e) {
            throw new NexusException("Failed to create scene: " + e.getMessage(), e);
        }
    }

    private void ensureDirectoryExists(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new NexusException("Failed to create directory: " + dir, e);
            }
        }
    }

    private long getFileSize(Path path) {
        try { return Files.size(path); }
        catch (IOException e) { return -1; }
    }

    private EndpointNode.Leaf leaf(EndpointHandler handler) {
        return new EndpointNode.Leaf(handler, Map.class, Object.class, EndpointHooks.none());
    }
}
