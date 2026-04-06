package dev.nexus.core.plugin.impl.slack;

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
public class SlackPlugin implements NexusPlugin {

    private static final String API_BASE = "https://slack.com/api";
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getId() { return "slack"; }

    @Override
    public AuthType getDefaultAuthType() { return AuthType.BOT_TOKEN; }

    @Override
    public EndpointNode getEndpoints() {
        return new EndpointNode.Group(Map.of(
                "channels", new EndpointNode.Group(Map.of(
                        "list", leaf(this::listChannels)
                )),
                "chat", new EndpointNode.Group(Map.of(
                        "postMessage", leaf(this::postMessage),
                        "history", leaf(this::chatHistory)
                ))
        ));
    }

    @Override
    public Map<String, WebhookDefinition> getWebhooks() {
        return Map.of("message", new WebhookDefinition(
                "slack.message",
                headers -> headers.containsKey("x-slack-signature"),
                (headers, body) -> new WebhookResult(true, "slack", "slack.message", body)
        ));
    }

    @Override
    public Optional<PluginWebhookMatcher> getPluginWebhookMatcher() {
        return Optional.of(headers ->
                headers.containsKey("x-slack-signature") || headers.containsKey("x-slack-request-timestamp"));
    }

    @Override
    public Map<String, EndpointMeta> getEndpointMeta() {
        return Map.of(
                "slack.channels.list", new EndpointMeta(RiskLevel.READ, "List Slack channels", false),
                "slack.chat.postMessage", new EndpointMeta(RiskLevel.WRITE, "Send a message to a channel", false),
                "slack.chat.history", new EndpointMeta(RiskLevel.READ, "Get message history for a channel", false)
        );
    }

    @Override
    public Map<String, EndpointSchemas> getEndpointSchemas() {
        ObjectNode emptyInput = mapper.createObjectNode().put("type", "object");

        ObjectNode postInput = mapper.createObjectNode().put("type", "object");
        ObjectNode postProps = postInput.putObject("properties");
        postProps.putObject("channel").put("type", "string");
        postProps.putObject("text").put("type", "string");
        postInput.putArray("required").add("channel").add("text");

        ObjectNode historyInput = mapper.createObjectNode().put("type", "object");
        ObjectNode histProps = historyInput.putObject("properties");
        histProps.putObject("channel").put("type", "string");
        histProps.putObject("limit").put("type", "integer");
        historyInput.putArray("required").add("channel");

        ObjectNode objectOutput = mapper.createObjectNode().put("type", "object");

        return Map.of(
                "slack.channels.list", new EndpointSchemas(emptyInput, objectOutput),
                "slack.chat.postMessage", new EndpointSchemas(postInput, objectOutput),
                "slack.chat.history", new EndpointSchemas(historyInput, objectOutput)
        );
    }

    @Override
    public PluginAuthConfig getAuthConfig() {
        return new PluginAuthConfig(List.of("bot_token"));
    }

    @Override
    public Optional<OAuthConfig> getOAuthConfig() { return Optional.empty(); }

    @Override
    public Optional<ErrorHandler> getErrorHandler() { return Optional.empty(); }

    // --- Endpoint handlers ---

    private Object listChannels(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = ctx.keyManager().getField("bot_token")
                .orElseThrow(() -> new NexusException("Slack bot token not configured"));

        return ctx.httpClient().clientFor(API_BASE, token)
                .get().uri("/conversations.list?types=public_channel,private_channel&limit=100")
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object postMessage(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = ctx.keyManager().getField("bot_token")
                .orElseThrow(() -> new NexusException("Slack bot token not configured"));
        Map<String, Object> params = (Map<String, Object>) args;

        Map<String, Object> body = Map.of(
                "channel", params.get("channel"),
                "text", params.get("text")
        );

        return ctx.httpClient().clientFor(API_BASE, token)
                .post().uri("/chat.postMessage")
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object chatHistory(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = ctx.keyManager().getField("bot_token")
                .orElseThrow(() -> new NexusException("Slack bot token not configured"));
        Map<String, Object> params = (Map<String, Object>) args;
        String channel = (String) params.get("channel");
        int limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : 20;

        return ctx.httpClient().clientFor(API_BASE, token)
                .get().uri("/conversations.history?channel={channel}&limit={limit}", channel, limit)
                .retrieve().bodyToMono(Object.class).block();
    }

    private EndpointNode.Leaf leaf(EndpointHandler handler) {
        return new EndpointNode.Leaf(handler, Map.class, Object.class, EndpointHooks.none());
    }
}
