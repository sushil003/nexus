package dev.nexus.core.db.repository;

import dev.nexus.core.db.entity.NexusPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NexusPermissionRepository extends JpaRepository<NexusPermission, UUID> {

    Optional<NexusPermission> findByPluginAndEndpointAndTenantIdAndStatus(
            String plugin, String endpoint, String tenantId, String status);

    @Modifying
    @Query("UPDATE NexusPermission p SET p.status = 'EXPIRED' WHERE p.expiresAt < :now AND p.status = 'PENDING'")
    int expireOldPermissions(Instant now);
}
