package dev.nexus.core.permission;

import dev.nexus.core.config.NexusProperties;
import dev.nexus.core.db.entity.NexusPermission;
import dev.nexus.core.db.repository.NexusPermissionRepository;
import dev.nexus.core.error.NexusException;
import dev.nexus.core.plugin.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class PermissionEnforcer {

    private static final Logger log = LoggerFactory.getLogger(PermissionEnforcer.class);
    private static final long PERMISSION_EXPIRY_MINUTES = 30;

    private final PermissionEvaluator evaluator;
    private final NexusPermissionRepository permissionRepo;
    private final PermissionMode mode;

    public PermissionEnforcer(PermissionEvaluator evaluator,
                              NexusPermissionRepository permissionRepo,
                              NexusProperties properties) {
        this.evaluator = evaluator;
        this.permissionRepo = permissionRepo;
        this.mode = properties.permission().mode();
    }

    public void enforce(String plugin, String endpoint, String tenantId,
                        RiskLevel riskLevel, String args) {
        PermissionResult result = evaluator.evaluate(riskLevel, mode);

        switch (result) {
            case ALLOW -> log.debug("Permission granted for {}.{} (risk={}, mode={})",
                    plugin, endpoint, riskLevel, mode);

            case DENY -> throw new NexusException(
                    "Operation denied: %s.%s requires %s access, but mode is %s"
                            .formatted(plugin, endpoint, riskLevel, mode));

            case REQUIRE_APPROVAL -> enforceApproval(plugin, endpoint, tenantId, args);
        }
    }

    private void enforceApproval(String plugin, String endpoint, String tenantId, String args) {
        Optional<NexusPermission> existing = permissionRepo
                .findByPluginAndEndpointAndTenantIdAndStatus(plugin, endpoint, tenantId, "APPROVED");

        if (existing.isPresent()) {
            NexusPermission perm = existing.get();
            if (perm.getExpiresAt() != null && perm.getExpiresAt().isBefore(Instant.now())) {
                perm.setStatus("EXPIRED");
                permissionRepo.save(perm);
            } else {
                perm.setStatus("EXECUTING");
                permissionRepo.save(perm);
                log.info("Using approved permission {} for {}.{}", perm.getId(), plugin, endpoint);
                return;
            }
        }

        // Check for existing pending request
        Optional<NexusPermission> pending = permissionRepo
                .findByPluginAndEndpointAndTenantIdAndStatus(plugin, endpoint, tenantId, "PENDING");

        if (pending.isPresent()) {
            throw new NexusException(
                    "Awaiting approval for %s.%s (permission ID: %s). Approve via POST /api/permissions/%s/approve"
                            .formatted(plugin, endpoint, pending.get().getId(), pending.get().getId()));
        }

        // Create new permission request
        NexusPermission permission = new NexusPermission();
        permission.setPlugin(plugin);
        permission.setEndpoint(endpoint);
        permission.setTenantId(tenantId);
        permission.setArgs(args);
        permission.setExpiresAt(Instant.now().plus(PERMISSION_EXPIRY_MINUTES, ChronoUnit.MINUTES));
        permission = permissionRepo.save(permission);

        throw new NexusException(
                "Approval required for %s.%s (permission ID: %s). Approve via POST /api/permissions/%s/approve"
                        .formatted(plugin, endpoint, permission.getId(), permission.getId()));
    }

    public void markCompleted(String plugin, String endpoint, String tenantId) {
        permissionRepo.findByPluginAndEndpointAndTenantIdAndStatus(plugin, endpoint, tenantId, "EXECUTING")
                .ifPresent(perm -> {
                    perm.setStatus("COMPLETED");
                    permissionRepo.save(perm);
                });
    }

    public void markFailed(String plugin, String endpoint, String tenantId, String error) {
        permissionRepo.findByPluginAndEndpointAndTenantIdAndStatus(plugin, endpoint, tenantId, "EXECUTING")
                .ifPresent(perm -> {
                    perm.setStatus("FAILED");
                    perm.setError(error);
                    permissionRepo.save(perm);
                });
    }
}
