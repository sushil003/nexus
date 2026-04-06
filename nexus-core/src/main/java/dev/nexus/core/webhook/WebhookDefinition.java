package dev.nexus.core.webhook;

import java.util.Map;
import java.util.function.Predicate;

public record WebhookDefinition(
        String id,
        Predicate<Map<String, String>> matcher,
        WebhookHandler handler
) {
}
