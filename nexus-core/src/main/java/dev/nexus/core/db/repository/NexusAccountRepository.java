package dev.nexus.core.db.repository;

import dev.nexus.core.db.entity.NexusAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NexusAccountRepository extends JpaRepository<NexusAccount, UUID> {
    Optional<NexusAccount> findByTenantIdAndIntegration_Name(String tenantId, String integrationName);
}
