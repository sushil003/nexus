package dev.nexus.core.endpoint;

import dev.nexus.core.auth.KeyManagerFactory;
import dev.nexus.core.auth.KeyManager;
import dev.nexus.core.context.PluginContext;
import dev.nexus.core.db.entity.NexusAccount;
import dev.nexus.core.db.repository.NexusAccountRepository;
import dev.nexus.core.db.repository.NexusEntityRepository;
import dev.nexus.core.error.NexusException;
import dev.nexus.core.event.EventService;
import dev.nexus.core.hook.BeforeHookResult;
import dev.nexus.core.http.NexusHttpClient;
import dev.nexus.core.permission.PermissionEnforcer;
import dev.nexus.core.plugin.EndpointMeta;
import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginRegistry;
import dev.nexus.core.plugin.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EndpointExecutor {

    private static final Logger log = LoggerFactory.getLogger(EndpointExecutor.class);

    private final PluginRegistry pluginRegistry;
    private final PermissionEnforcer permissionEnforcer;
    private final KeyManagerFactory keyManagerFactory;
    private final NexusHttpClient httpClient;
    private final NexusAccountRepository accountRepo;
    private final NexusEntityRepository entityRepo;
    private final EventService eventService;

    public EndpointExecutor(PluginRegistry pluginRegistry,
                            PermissionEnforcer permissionEnforcer,
                            KeyManagerFactory keyManagerFactory,
                            NexusHttpClient httpClient,
                            NexusAccountRepository accountRepo,
                            NexusEntityRepository entityRepo,
                            EventService eventService) {
        this.pluginRegistry = pluginRegistry;
        this.permissionEnforcer = permissionEnforcer;
        this.keyManagerFactory = keyManagerFactory;
        this.httpClient = httpClient;
        this.accountRepo = accountRepo;
        this.entityRepo = entityRepo;
        this.eventService = eventService;
    }

    public Object execute(String path, Object args, String tenantId) {
        String[] segments = path.split("\\.", 2);
        if (segments.length < 2) {
            throw new NexusException("Invalid endpoint path: " + path + ". Expected format: plugin.endpoint.path");
        }

        String pluginId = segments[0];
        String endpointPath = segments[1];

        NexusPlugin plugin = pluginRegistry.get(pluginId)
                .orElseThrow(() -> new NexusException("Unknown plugin: " + pluginId));

        // Walk the endpoint tree
        EndpointNode.Leaf leaf = resolveLeaf(plugin.getEndpoints(), endpointPath.split("\\."), 0);

        // Permission check (before retry)
        RiskLevel riskLevel = resolveRiskLevel(plugin, path);
        String argsString = args != null ? args.toString() : null;
        permissionEnforcer.enforce(pluginId, endpointPath, tenantId, riskLevel, argsString);

        // Build context
        NexusAccount account = accountRepo.findByTenantIdAndIntegration_Name(tenantId, pluginId)
                .orElseThrow(() -> new NexusException("No account configured for plugin: " + pluginId));
        KeyManager keyManager = keyManagerFactory.create(account);
        PluginContext ctx = new PluginContext(httpClient, keyManager, tenantId, entityRepo);

        try {
            Object result = executeWithRetry(leaf, ctx, args, path);

            // Log success event
            eventService.logSuccess(account, path, Map.of("args", String.valueOf(args)));

            // Mark permission as completed
            permissionEnforcer.markCompleted(pluginId, endpointPath, tenantId);

            return result;
        } catch (Exception e) {
            eventService.logFailure(account, path, Map.of(
                    "args", String.valueOf(args),
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            permissionEnforcer.markFailed(pluginId, endpointPath, tenantId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            throw e;
        }
    }

    @Retryable(
            retryFor = NexusException.class,
            noRetryFor = {},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public Object executeWithRetry(EndpointNode.Leaf leaf, PluginContext ctx, Object args, String path) {
        // Before hook
        Object effectiveArgs = args;
        String passToAfter = null;
        boolean beforeRan = false;

        if (leaf.hooks().before().isPresent()) {
            BeforeHookResult hookResult = leaf.hooks().before().get().execute(ctx, args);
            if (!hookResult.shouldContinue()) {
                log.info("Before hook aborted execution for: {}", path);
                return Map.of("status", "aborted", "message", "Execution aborted by before hook");
            }
            effectiveArgs = hookResult.modifiedArgs();
            passToAfter = hookResult.passToAfter();
            beforeRan = true;
        }

        // Execute handler
        Object result;
        try {
            result = leaf.handler().execute(ctx, effectiveArgs);
        } catch (NexusException e) {
            if (e.isRetryable()) {
                throw e; // Let @Retryable handle it
            }
            throw e;
        }

        // After hook (only if before ran and handler succeeded)
        if (beforeRan && leaf.hooks().after().isPresent()) {
            leaf.hooks().after().get().execute(ctx, result, passToAfter);
        }

        return result;
    }

    private EndpointNode.Leaf resolveLeaf(EndpointNode node, String[] segments, int index) {
        return switch (node) {
            case EndpointNode.Leaf leaf -> {
                if (index < segments.length) {
                    throw new NexusException("Endpoint path too deep: extra segments after leaf");
                }
                yield leaf;
            }
            case EndpointNode.Group group -> {
                if (index >= segments.length) {
                    throw new NexusException("Endpoint path too short: reached group without selecting endpoint");
                }
                EndpointNode child = group.children().get(segments[index]);
                if (child == null) {
                    throw new NexusException("Unknown endpoint segment: " + segments[index]);
                }
                yield resolveLeaf(child, segments, index + 1);
            }
        };
    }

    private RiskLevel resolveRiskLevel(NexusPlugin plugin, String path) {
        Map<String, EndpointMeta> meta = plugin.getEndpointMeta();
        EndpointMeta endpointMeta = meta.get(path);
        if (endpointMeta != null) {
            return endpointMeta.riskLevel();
        }
        // Default to WRITE if no metadata found
        return RiskLevel.WRITE;
    }
}
