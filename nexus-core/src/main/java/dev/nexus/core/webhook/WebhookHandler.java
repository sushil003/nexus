package dev.nexus.core.webhook;

import java.util.Map;

@FunctionalInterface
public interface WebhookHandler {
    WebhookResult handle(Map<String, String> headers, String body);
}
