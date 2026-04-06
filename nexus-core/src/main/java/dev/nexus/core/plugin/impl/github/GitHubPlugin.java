package dev.nexus.core.plugin.impl.github;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GitHubPlugin implements NexusPlugin {

    private static final String API_BASE = "https://api.github.com";
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getId() { return "github"; }

    @Override
    public AuthType getDefaultAuthType() { return AuthType.API_KEY; }

    @Override
    public EndpointNode getEndpoints() {
        return new EndpointNode.Group(Map.of(
                "repos", new EndpointNode.Group(Map.of(
                        "list", leaf(this::listRepos)
                )),
                "issues", new EndpointNode.Group(Map.of(
                        "list", leaf(this::listIssues),
                        "create", leaf(this::createIssue)
                ))
        ));
    }

    @Override
    public Map<String, WebhookDefinition> getWebhooks() {
        return Map.of("push", new WebhookDefinition(
                "github.push",
                headers -> "push".equals(headers.get("x-github-event")),
                (headers, body) -> new WebhookResult(true, "github", "github.push", body)
        ));
    }

    @Override
    public Optional<PluginWebhookMatcher> getPluginWebhookMatcher() {
        return Optional.of(headers -> headers.containsKey("x-github-event"));
    }

    @Override
    public Map<String, EndpointMeta> getEndpointMeta() {
        return Map.of(
                "github.repos.list", new EndpointMeta(RiskLevel.READ, "List authenticated user's repositories", false),
                "github.issues.list", new EndpointMeta(RiskLevel.READ, "List issues for a repository", false),
                "github.issues.create", new EndpointMeta(RiskLevel.WRITE, "Create a new issue", false)
        );
    }

    @Override
    public Map<String, EndpointSchemas> getEndpointSchemas() {
        ObjectNode emptyInput = mapper.createObjectNode().put("type", "object");

        ObjectNode issueListInput = mapper.createObjectNode().put("type", "object");
        ObjectNode ilProps = issueListInput.putObject("properties");
        ilProps.putObject("owner").put("type", "string");
        ilProps.putObject("repo").put("type", "string");
        issueListInput.putArray("required").add("owner").add("repo");

        ObjectNode issueCreateInput = mapper.createObjectNode().put("type", "object");
        ObjectNode icProps = issueCreateInput.putObject("properties");
        icProps.putObject("owner").put("type", "string");
        icProps.putObject("repo").put("type", "string");
        icProps.putObject("title").put("type", "string");
        icProps.putObject("body").put("type", "string");
        issueCreateInput.putArray("required").add("owner").add("repo").add("title");

        ObjectNode arrayOutput = mapper.createObjectNode().put("type", "array");

        return Map.of(
                "github.repos.list", new EndpointSchemas(emptyInput, arrayOutput),
                "github.issues.list", new EndpointSchemas(issueListInput, arrayOutput),
                "github.issues.create", new EndpointSchemas(issueCreateInput, mapper.createObjectNode().put("type", "object"))
        );
    }

    @Override
    public PluginAuthConfig getAuthConfig() {
        return new PluginAuthConfig(List.of("api_key"));
    }

    @Override
    public Optional<OAuthConfig> getOAuthConfig() { return Optional.empty(); }

    @Override
    public Optional<ErrorHandler> getErrorHandler() { return Optional.empty(); }

    // --- Endpoint handlers ---

    private Object listRepos(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = ctx.keyManager().getField("api_key")
                .orElseThrow(() -> new NexusException("GitHub API key not configured"));

        return ctx.httpClient().clientFor(API_BASE, token)
                .get().uri("/user/repos?per_page=30&sort=updated")
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object listIssues(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = ctx.keyManager().getField("api_key")
                .orElseThrow(() -> new NexusException("GitHub API key not configured"));
        Map<String, Object> params = (Map<String, Object>) args;
        String owner = (String) params.get("owner");
        String repo = (String) params.get("repo");

        return ctx.httpClient().clientFor(API_BASE, token)
                .get().uri("/repos/{owner}/{repo}/issues", owner, repo)
                .retrieve().bodyToMono(Object.class).block();
    }

    @SuppressWarnings("unchecked")
    private Object createIssue(dev.nexus.core.context.PluginContext ctx, Object args) {
        String token = ctx.keyManager().getField("api_key")
                .orElseThrow(() -> new NexusException("GitHub API key not configured"));
        Map<String, Object> params = (Map<String, Object>) args;
        String owner = (String) params.get("owner");
        String repo = (String) params.get("repo");

        Map<String, Object> body = Map.of(
                "title", params.get("title"),
                "body", params.getOrDefault("body", "")
        );

        return ctx.httpClient().clientFor(API_BASE, token)
                .post().uri("/repos/{owner}/{repo}/issues", owner, repo)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block();
    }

    private EndpointNode.Leaf leaf(EndpointHandler handler) {
        return new EndpointNode.Leaf(handler, Map.class, Object.class, EndpointHooks.none());
    }
}
