package dev.nexus.core.inspect;

import dev.nexus.core.plugin.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectServiceTest {

    @Mock private PluginRegistry pluginRegistry;
    @Mock private NexusPlugin githubPlugin;

    private InspectService inspectService;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        inspectService = new InspectService(pluginRegistry);
    }

    @Test
    void listOperations_returnsAllEndpoints() {
        when(pluginRegistry.getAll()).thenReturn(Map.of("github", githubPlugin));
        when(githubPlugin.getEndpointMeta()).thenReturn(Map.of(
                "github.repos.list", new EndpointMeta(RiskLevel.READ, "List repositories", false),
                "github.issues.create", new EndpointMeta(RiskLevel.WRITE, "Create an issue", false)));

        List<InspectService.EndpointInfo> ops = inspectService.listOperations(null);

        assertEquals(2, ops.size());
        assertTrue(ops.stream().anyMatch(o -> o.path().equals("github.repos.list")));
        assertTrue(ops.stream().anyMatch(o -> o.path().equals("github.issues.create")));
    }

    @Test
    void listOperations_withFilter_returnsOnlyMatchingPlugin() {
        when(pluginRegistry.get("github")).thenReturn(Optional.of(githubPlugin));
        when(githubPlugin.getEndpointMeta()).thenReturn(Map.of(
                "github.repos.list", new EndpointMeta(RiskLevel.READ, "List repos", false)));

        List<InspectService.EndpointInfo> ops = inspectService.listOperations("github");

        assertEquals(1, ops.size());
        assertEquals("github.repos.list", ops.getFirst().path());
    }

    @Test
    void listOperations_unknownPlugin_returnsEmpty() {
        when(pluginRegistry.get("unknown")).thenReturn(Optional.empty());

        List<InspectService.EndpointInfo> ops = inspectService.listOperations("unknown");
        assertTrue(ops.isEmpty());
    }

    @Test
    void getSchema_returnsSchemaForKnownEndpoint() {
        JsonNode inputSchema = mapper.createObjectNode().put("type", "object");
        JsonNode outputSchema = mapper.createObjectNode().put("type", "array");

        when(pluginRegistry.get("github")).thenReturn(Optional.of(githubPlugin));
        when(githubPlugin.getEndpointSchemas()).thenReturn(Map.of(
                "github.repos.list", new EndpointSchemas(inputSchema, outputSchema)));
        when(githubPlugin.getEndpointMeta()).thenReturn(Map.of(
                "github.repos.list", new EndpointMeta(RiskLevel.READ, "List repos", false)));

        Optional<InspectService.SchemaInfo> result = inspectService.getSchema("github.repos.list");

        assertTrue(result.isPresent());
        assertEquals("github.repos.list", result.get().path());
        assertEquals("List repos", result.get().description());
        assertEquals(inputSchema, result.get().inputSchema());
        assertEquals(outputSchema, result.get().outputSchema());
    }

    @Test
    void getSchema_unknownEndpoint_returnsEmpty() {
        when(pluginRegistry.get("github")).thenReturn(Optional.of(githubPlugin));
        when(githubPlugin.getEndpointSchemas()).thenReturn(Map.of());

        Optional<InspectService.SchemaInfo> result = inspectService.getSchema("github.unknown.path");
        assertTrue(result.isEmpty());
    }

    @Test
    void getSchema_invalidPath_returnsEmpty() {
        Optional<InspectService.SchemaInfo> result = inspectService.getSchema("nodots");
        assertTrue(result.isEmpty());
    }
}
