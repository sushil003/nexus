package dev.nexus.core.inspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaToTypeStringTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertString() {
        ObjectNode schema = mapper.createObjectNode().put("type", "string");
        assertEquals("string", JsonSchemaToTypeString.convert(schema));
    }

    @Test
    void convertInteger() {
        ObjectNode schema = mapper.createObjectNode().put("type", "integer");
        assertEquals("integer", JsonSchemaToTypeString.convert(schema));
    }

    @Test
    void convertBoolean() {
        ObjectNode schema = mapper.createObjectNode().put("type", "boolean");
        assertEquals("boolean", JsonSchemaToTypeString.convert(schema));
    }

    @Test
    void convertArray() {
        ObjectNode schema = mapper.createObjectNode().put("type", "array");
        schema.putObject("items").put("type", "string");
        assertEquals("string[]", JsonSchemaToTypeString.convert(schema));
    }

    @Test
    void convertObject() {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("name").put("type", "string");
        props.putObject("age").put("type", "integer");
        schema.putArray("required").add("name");

        String result = JsonSchemaToTypeString.convert(schema);
        assertTrue(result.contains("name: string"));
        assertTrue(result.contains("age?: integer"));
    }

    @Test
    void convertEnum() {
        ObjectNode schema = mapper.createObjectNode().put("type", "string");
        schema.putArray("enum").add("open").add("closed");
        assertEquals("\"open\" | \"closed\"", JsonSchemaToTypeString.convert(schema));
    }

    @Test
    void convertNull_returnsUnknown() {
        assertEquals("unknown", JsonSchemaToTypeString.convert(null));
    }
}
