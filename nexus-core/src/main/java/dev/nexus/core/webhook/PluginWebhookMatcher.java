package dev.nexus.core.webhook;

import java.util.Map;

@FunctionalInterface
public interface PluginWebhookMatcher {
    boolean matches(Map<String, String> headers);
}
