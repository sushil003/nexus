package dev.nexus.core.plugin;

import com.fasterxml.jackson.databind.JsonNode;

public record EndpointSchemas(JsonNode inputSchema, JsonNode outputSchema) {
}
