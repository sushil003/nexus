# Plugin Guide

## Creating a Plugin

Plugins implement the `NexusPlugin` interface and are annotated with `@Component` for auto-discovery.

### Minimal Plugin

```java
@Component
public class MyPlugin implements NexusPlugin {

    @Override
    public String getId() { return "myplugin"; }

    @Override
    public AuthType getDefaultAuthType() { return AuthType.API_KEY; }

    @Override
    public EndpointNode getEndpoints() {
        return new EndpointNode.Group(Map.of(
            "items", new EndpointNode.Group(Map.of(
                "list", new EndpointNode.Leaf(
                    this::listItems,
                    Map.class,
                    Object.class,
                    EndpointHooks.none()
                )
            ))
        ));
    }

    private Object listItems(PluginContext ctx, Object args) {
        String apiKey = ctx.keyManager().getField("api_key")
                .orElseThrow(() -> new NexusException("API key not configured"));

        return ctx.httpClient().clientFor("https://api.example.com", apiKey)
                .get().uri("/items")
                .retrieve().bodyToMono(Object.class).block();
    }

    // ... implement remaining NexusPlugin methods
}
```

### NexusPlugin Interface

| Method | Required | Description |
|--------|----------|-------------|
| `getId()` | Yes | Unique plugin identifier (e.g., "github") |
| `getDefaultAuthType()` | Yes | `API_KEY`, `OAUTH2`, `BOT_TOKEN`, or `NONE` |
| `getEndpoints()` | Yes | Endpoint tree (Groups + Leaves) |
| `getWebhooks()` | Yes | Map of webhook definitions (can be empty) |
| `getPluginWebhookMatcher()` | Yes | Optional quick-filter for webhooks |
| `getEndpointMeta()` | Yes | Risk level + description per endpoint path |
| `getEndpointSchemas()` | Yes | JSON Schema for input/output per endpoint |
| `getAuthConfig()` | Yes | Required credential field names |
| `getOAuthConfig()` | Yes | Optional OAuth2 configuration |
| `getErrorHandler()` | Yes | Optional custom error handler for retry decisions |

### Endpoint Handlers

Handlers receive a `PluginContext` with:
- `httpClient` — `NexusHttpClient` for making API calls
- `keyManager` — Access to decrypted credentials
- `tenantId` — Current tenant
- `entityRepo` — JPA repository for storing plugin data

### Adding Endpoint Metadata

Every endpoint path must have a corresponding `EndpointMeta` entry:

```java
@Override
public Map<String, EndpointMeta> getEndpointMeta() {
    return Map.of(
        "myplugin.items.list", new EndpointMeta(RiskLevel.READ, "List all items", false),
        "myplugin.items.delete", new EndpointMeta(RiskLevel.DESTRUCTIVE, "Delete an item", true)
    );
}
```

### Adding Schemas

Schemas enable the `get_schema` MCP tool to show input/output types:

```java
@Override
public Map<String, EndpointSchemas> getEndpointSchemas() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode input = mapper.createObjectNode().put("type", "object");
    input.putObject("properties").putObject("id").put("type", "string");
    input.putArray("required").add("id");

    return Map.of(
        "myplugin.items.get", new EndpointSchemas(input, mapper.createObjectNode().put("type", "object"))
    );
}
```

### Webhooks

Define webhooks with a matcher predicate and handler:

```java
@Override
public Map<String, WebhookDefinition> getWebhooks() {
    return Map.of("item.created", new WebhookDefinition(
        "myplugin.item.created",
        headers -> "item_created".equals(headers.get("x-event-type")),
        (headers, body) -> new WebhookResult(true, "myplugin", "myplugin.item.created", body)
    ));
}
```

### OAuth2 Plugins

For OAuth2 plugins, return an `OAuthConfig`:

```java
@Override
public Optional<OAuthConfig> getOAuthConfig() {
    return Optional.of(new OAuthConfig(
        "provider-name",
        "https://provider.com/oauth/authorize",
        "https://provider.com/oauth/token",
        List.of("scope1", "scope2"),
        TokenAuthMethod.CLIENT_SECRET_POST,
        true,
        Map.of()
    ));
}
```

Users complete the flow via:
1. `POST /api/credentials/myplugin` with `client_id` and `client_secret`
2. `GET /oauth/myplugin/start` — returns the auth URL
3. Browser redirects back to `/oauth/myplugin/callback` — tokens stored automatically

## Existing Plugins

| Plugin | Package | Auth | Endpoints |
|--------|---------|------|-----------|
| GitHub | `dev.nexus.core.plugin.impl.github` | API_KEY | 3 |
| Slack | `dev.nexus.core.plugin.impl.slack` | BOT_TOKEN | 3 |
| Excalidraw | `dev.nexus.core.plugin.impl.excalidraw` | NONE | 3 |
| Miro | `dev.nexus.core.plugin.impl.miro` | OAUTH2 | 5 |
