package dev.nexus.core.inspect;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;

public final class JsonSchemaToTypeString {

    private JsonSchemaToTypeString() {}

    public static String convert(JsonNode schema) {
        if (schema == null || schema.isMissingNode()) {
            return "unknown";
        }
        return convertNode(schema);
    }

    private static String convertNode(JsonNode node) {
        if (node == null) return "unknown";

        String type = node.has("type") ? node.get("type").asText() : null;
        if (type == null) return "unknown";

        return switch (type) {
            case "string" -> {
                if (node.has("enum")) {
                    StringJoiner joiner = new StringJoiner(" | ");
                    node.get("enum").forEach(v -> joiner.add("\"" + v.asText() + "\""));
                    yield joiner.toString();
                }
                yield "string";
            }
            case "integer", "number" -> type;
            case "boolean" -> "boolean";
            case "array" -> {
                JsonNode items = node.get("items");
                yield convertNode(items) + "[]";
            }
            case "object" -> {
                JsonNode properties = node.get("properties");
                if (properties == null || properties.isEmpty()) {
                    yield "object";
                }
                StringJoiner joiner = new StringJoiner(", ", "{ ", " }");
                JsonNode required = node.get("required");
                Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String fieldName = field.getKey();
                    boolean isRequired = required != null && containsText(required, fieldName);
                    String suffix = isRequired ? "" : "?";
                    joiner.add(fieldName + suffix + ": " + convertNode(field.getValue()));
                }
                yield joiner.toString();
            }
            default -> type;
        };
    }

    private static boolean containsText(JsonNode arrayNode, String text) {
        for (JsonNode element : arrayNode) {
            if (text.equals(element.asText())) return true;
        }
        return false;
    }
}
