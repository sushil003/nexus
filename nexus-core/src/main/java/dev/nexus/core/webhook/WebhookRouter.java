package dev.nexus.core.webhook;

import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class WebhookRouter {

    private static final Logger log = LoggerFactory.getLogger(WebhookRouter.class);

    private final PluginRegistry pluginRegistry;

    public WebhookRouter(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public Optional<WebhookResult> route(String pluginId, Map<String, String> headers, String body) {
        Optional<NexusPlugin> pluginOpt = pluginRegistry.get(pluginId);
        if (pluginOpt.isEmpty()) {
            log.warn("Webhook received for unknown plugin: {}", pluginId);
            return Optional.empty();
        }

        NexusPlugin plugin = pluginOpt.get();

        // Level 1: Plugin-level matcher (quick filter)
        Optional<PluginWebhookMatcher> pluginMatcher = plugin.getPluginWebhookMatcher();
        if (pluginMatcher.isPresent() && !pluginMatcher.get().matches(headers)) {
            log.debug("Webhook rejected by plugin-level matcher for: {}", pluginId);
            return Optional.empty();
        }

        // Level 2: Individual webhook matchers
        Map<String, WebhookDefinition> webhooks = plugin.getWebhooks();
        for (var entry : webhooks.entrySet()) {
            WebhookDefinition webhook = entry.getValue();
            if (webhook.matcher().test(headers)) {
                log.info("Webhook matched: plugin={}, webhook={}", pluginId, webhook.id());
                try {
                    WebhookResult result = webhook.handler().handle(headers, body);
                    return Optional.of(result);
                } catch (Exception e) {
                    log.error("Webhook handler failed: plugin={}, webhook={}", pluginId, webhook.id(), e);
                    return Optional.of(new WebhookResult(false, pluginId, webhook.id(), e.getMessage()));
                }
            }
        }

        log.debug("No webhook matched for plugin: {}", pluginId);
        return Optional.empty();
    }
}
