package dev.nexus.core.plugin.impl.miro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nexus.core.endpoint.EndpointHandler;
import dev.nexus.core.endpoint.EndpointNode;
import dev.nexus.core.error.ErrorHandler;
import dev.nexus.core.error.NexusException;
import dev.nexus.core.hook.EndpointHooks;
import dev.nexus.core.plugin.*;
import dev.nexus.core.webhook.PluginWebhookMatcher;
import dev.nexus.core.webhook.WebhookDefinition;
import dev.nexus.core.webhook.WebhookResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MiroPlugin implements NexusPlugin {

    private static final String API_BASE = "https://api.miro.com/v2";
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getId() { return "miro"; }

    @Override
    public AuthType getDefaultAuthType() { return AuthType.OAUTH2; }

    @Override
    public EndpointNode getEndpoints() {
        return new EndpointNode.Group(Map.of(
                "boards", new EndpointNode.Group(Map.of(
                        "list", leaf(this::listBoards),
                        "get", leaf(this::getBoard)
                )),
                "items", new EndpointNode.Group(Map.of(
                        "list", leaf(this::listItems)
                )),
                "stickyNotes", new EndpointNode.Group(Map.of(
                        "create", leaf(this::createStickyNote)
                )),
                "shapes", new EndpointNode.Group(Map.of(
                        "create", leaf(this::createShape)
                ))
        ));
    }

    @Override
    public Map<String, WebhookDefinition> getWebhooks() {
        return Map.of("board.updated", new WebhookDefinition(
                "miro.board.updated",
                headers -> "board_subscription".equals(headers.get("x-miro-event")),
                (headers, body) -> new WebhookResult(true, "miro", "miro.board.updated", body)
        ));
    }

    @Override
    public Optional<PluginWebhookMatcher> getPluginWebhookMatcher() {
        return Optional.of(headers -> headers.containsKey("x-miro-event"));
    }

    @Override
    public Map<String, EndpointMeta> getEndpointMeta() {
        return Map.of(
                "miro.boards.list", new EndpointMeta(RiskLevel.READ, "List all boards", false),
                "miro.boards.get", new EndpointMeta(RiskLevel.READ, "Get a specific board", false),
                "miro.items.list", new EndpointMeta(RiskLevel.READ, "List items on a board", false),
                "miro.stickyNotes.create", new EndpointMeta(RiskLevel.WRITE, "Create a sticky note on a board", false),
                "miro.shapes.create", new EndpointMeta(RiskLevel.WRITE, "Create a shape on a board", false)
        );
    }

    @Override
    public Map<String, EndpointSchemas> getEndpointSchemas() {
        ObjectNode emptyInput = mapper.createObjectNode().put("type", "object");

        ObjectNode boardIdInput = mapper.createObjectNode().put("type", "object");
        boardIdInput.putObject("properties").putObject("board_id").put("type", "string");
        boardIdInput.putArray("required").add("board_id");

        ObjectNode stickyInput = mapper.createObjectNode().put("type", "object");
        ObjectNode stickyProps = stickyInput.putObject("properties");
        stickyProps.putObject("board_id").put("type", "string");
        stickyProps.putObject("content").put("type", "string");
        ObjectNode stickyPos = stickyProps.putObject("position").put("type", "object");
        ObjectNode spProps = stickyPos.putObject("properties");
        spProps.putObject("x").put("type", "number");
        spProps.putObject("y").put("type", "number");
        stickyInput.putArray("required").add("board_id").add("content");

        ObjectNode shapeInput = mapper.createObjectNode().put("type", "object");
        ObjectNode shapeProps = shapeInput.putObject("properties");
        shapeProps.putObject("board_id").put("type", "string");
        shapeProps.putObject("shape").put("type", "string");
        shapeProps.putObject("content").put("type", "string");
        ObjectNode shapePos = shapeProps.putObject("position").put("type", "object");
        ObjectNode shpProps = shapePos.putObject("properties");
        shpProps.putObject("x").put("type", "number");
        shpProps.putObject("y").put("type", "number");
        shapeInput.putArray("required").add("board_id").add("shape");

        ObjectNode objectOutput = mapper.createObjectNode().put("type", "object");

        return Map.of(
                "miro.boards.list", new EndpointSchemas(emptyInput, objectOutput),
                "miro.boards.get", new EndpointSchemas(boardIdInput, objectOutput),
                "miro.items.list", new EndpointSchemas(boardIdInput, objectOutput),
                "miro.stickyNotes.create", new EndpointSchemas(stickyInput, objectOutput),
                "miro.shapes.create", new EndpointSchemas(shapeInput, objectOutput)
        );
    }

    @Override
    public PluginAuthConfig getAuthConfig() {
        return new PluginAuthConfig(List.of("client_id", "client_secret"));
    }

    @Override
    public Optional<OAuthConfig> getOAuthConfig() {
        return Optional.of(new OAuthConfig(
                "miro",
                "https://miro.com/oauth/authorize",
                "https://api.miro.com/v1/oauth/token",
                List.of("boards:read", "boards:write"),
                TokenAuthMethod.CLIENT_SECRET_POST,
                true,
                Map.of("response_type", "code")
        ));
    }

    @Override
    public Optional<ErrorHandler> getErrorHandler() { return Optional.empty(); }

    // --- Endpoint handlers ---

    private Object listBoards(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = getAccessToken(ctx);
        return ctx.httpClient().clientFor(API_BASE, token)
                .get().uri("/boards")
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object getBoard(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = getAccessToken(ctx);
        Map<String, Object> params = (Map<String, Object>) args;
        String boardId = (String) params.get("board_id");

        return ctx.httpClient().clientFor(API_BASE, token)
                .get().uri("/boards/{id}", boardId)
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object listItems(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = getAccessToken(ctx);
        Map<String, Object> params = (Map<String, Object>) args;
        String boardId = (String) params.get("board_id");

        return ctx.httpClient().clientFor(API_BASE, token)
                .get().uri("/boards/{id}/items", boardId)
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object createStickyNote(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = getAccessToken(ctx);
        Map<String, Object> params = (Map<String, Object>) args;
        String boardId = (String) params.get("board_id");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("data", Map.of("content", params.get("content")));
        if (params.containsKey("position")) {
            body.put("position", params.get("position"));
        }

        return ctx.httpClient().clientFor(API_BASE, token)
                .post().uri("/boards/{id}/sticky_notes", boardId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object createShape(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = getAccessToken(ctx);
        Map<String, Object> params = (Map<String, Object>) args;
        String boardId = (String) params.get("board_id");

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("shape", params.get("shape"));
        if (params.containsKey("content")) {
            data.put("content", params.get("content"));
        }

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("data", data);
        if (params.containsKey("position")) {
            body.put("position", params.get("position"));
        }

        return ctx.httpClient().clientFor(API_BASE, token)
                .post().uri("/boards/{id}/shapes", boardId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block();
    }

    private String getAccessToken(dev.nexus.core.context.PluginContext ctx) {
        return ctx.keyManager().getField("access_token")
                .orElseThrow(() -> new NexusException(
                        "Miro not authenticated. Complete OAuth flow: POST /api/credentials/miro with client_id and client_secret, then GET /oauth/miro/start"));
    }

    private EndpointNode.Leaf leaf(EndpointHandler handler) {
        return new EndpointNode.Leaf(handler, Map.class, Object.class, EndpointHooks.none());
    }
}
