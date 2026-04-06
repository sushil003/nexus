package dev.nexus.core.endpoint;

import dev.nexus.core.auth.KeyManager;
import dev.nexus.core.auth.KeyManagerFactory;
import dev.nexus.core.context.PluginContext;
import dev.nexus.core.db.entity.NexusAccount;
import dev.nexus.core.db.entity.NexusIntegration;
import dev.nexus.core.db.repository.NexusAccountRepository;
import dev.nexus.core.db.repository.NexusEntityRepository;
import dev.nexus.core.error.NexusException;
import dev.nexus.core.event.EventService;
import dev.nexus.core.hook.EndpointHooks;
import dev.nexus.core.http.NexusHttpClient;
import dev.nexus.core.permission.PermissionEnforcer;
import dev.nexus.core.plugin.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointExecutorTest {

    @Mock private PluginRegistry pluginRegistry;
    @Mock private PermissionEnforcer permissionEnforcer;
    @Mock private KeyManagerFactory keyManagerFactory;
    @Mock private NexusHttpClient httpClient;
    @Mock private NexusAccountRepository accountRepo;
    @Mock private NexusEntityRepository entityRepo;
    @Mock private EventService eventService;
    @Mock private NexusPlugin plugin;
    @Mock private KeyManager keyManager;

    private EndpointExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EndpointExecutor(pluginRegistry, permissionEnforcer, keyManagerFactory,
                httpClient, accountRepo, entityRepo, eventService);
    }

    @Test
    void execute_happyPath() {
        // Setup plugin with a simple endpoint
        EndpointNode.Leaf leaf = new EndpointNode.Leaf(
                (ctx, args) -> Map.of("result", "success"),
                Map.class, Map.class, EndpointHooks.none());

        EndpointNode endpoints = new EndpointNode.Group(Map.of(
                "repos", new EndpointNode.Group(Map.of("list", leaf))));

        when(pluginRegistry.get("github")).thenReturn(Optional.of(plugin));
        when(plugin.getEndpoints()).thenReturn(endpoints);
        when(plugin.getEndpointMeta()).thenReturn(Map.of(
                "github.repos.list", new EndpointMeta(RiskLevel.READ, "List repos", false)));

        NexusAccount account = createTestAccount();
        when(accountRepo.findByTenantIdAndIntegration_Name("default", "github"))
                .thenReturn(Optional.of(account));
        when(keyManagerFactory.create(account)).thenReturn(keyManager);

        Object result = executor.execute("github.repos.list", null, "default");

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, String> resultMap = (Map<String, String>) result;
        assertEquals("success", resultMap.get("result"));

        verify(permissionEnforcer).enforce(eq("github"), eq("repos.list"), eq("default"), eq(RiskLevel.READ), any());
        verify(eventService).logSuccess(eq(account), eq("github.repos.list"), any());
        verify(permissionEnforcer).markCompleted("github", "repos.list", "default");
    }

    @Test
    void execute_unknownPlugin_throws() {
        when(pluginRegistry.get("unknown")).thenReturn(Optional.empty());

        assertThrows(NexusException.class, () ->
                executor.execute("unknown.endpoint", null, "default"));
    }

    @Test
    void execute_invalidPath_throws() {
        assertThrows(NexusException.class, () ->
                executor.execute("noDots", null, "default"));
    }

    @Test
    void execute_handlerThrows_logsFailure() {
        EndpointNode.Leaf leaf = new EndpointNode.Leaf(
                (ctx, args) -> { throw new NexusException("API error"); },
                Map.class, Map.class, EndpointHooks.none());

        EndpointNode endpoints = new EndpointNode.Group(Map.of("action", leaf));

        when(pluginRegistry.get("test")).thenReturn(Optional.of(plugin));
        when(plugin.getEndpoints()).thenReturn(endpoints);
        when(plugin.getEndpointMeta()).thenReturn(Map.of());

        NexusAccount account = createTestAccount();
        when(accountRepo.findByTenantIdAndIntegration_Name("default", "test"))
                .thenReturn(Optional.of(account));
        when(keyManagerFactory.create(account)).thenReturn(keyManager);

        assertThrows(NexusException.class, () ->
                executor.execute("test.action", null, "default"));

        verify(eventService).logFailure(eq(account), eq("test.action"), any());
        verify(permissionEnforcer).markFailed(eq("test"), eq("action"), eq("default"), eq("API error"));
    }

    @Test
    void resolveLeaf_tooDeepPath_throws() {
        EndpointNode.Leaf leaf = new EndpointNode.Leaf(
                (ctx, args) -> null, Map.class, Map.class, EndpointHooks.none());

        EndpointNode endpoints = new EndpointNode.Group(Map.of("action", leaf));

        when(pluginRegistry.get("test")).thenReturn(Optional.of(plugin));
        when(plugin.getEndpoints()).thenReturn(endpoints);

        assertThrows(NexusException.class, () ->
                executor.execute("test.action.deep", null, "default"));
    }

    private NexusAccount createTestAccount() {
        NexusIntegration integration = new NexusIntegration();
        integration.setName("test");
        NexusAccount account = new NexusAccount();
        account.setTenantId("default");
        account.setIntegration(integration);
        return account;
    }
}
