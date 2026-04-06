package dev.nexus.core.plugin;

import dev.nexus.core.endpoint.EndpointNode;
import dev.nexus.core.error.ErrorHandler;
import dev.nexus.core.webhook.PluginWebhookMatcher;
import dev.nexus.core.webhook.WebhookDefinition;

import java.util.Map;
import java.util.Optional;

public interface NexusPlugin {

    String getId();

    AuthType getDefaultAuthType();

    EndpointNode getEndpoints();

    Map<String, WebhookDefinition> getWebhooks();

    Optional<PluginWebhookMatcher> getPluginWebhookMatcher();

    Map<String, EndpointMeta> getEndpointMeta();

    Map<String, EndpointSchemas> getEndpointSchemas();

    PluginAuthConfig getAuthConfig();

    Optional<OAuthConfig> getOAuthConfig();

    Optional<ErrorHandler> getErrorHandler();
}
