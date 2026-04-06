package dev.nexus.core.webhook;

public record WebhookResult(boolean success, String pluginId, String webhookId, Object data) {
}
