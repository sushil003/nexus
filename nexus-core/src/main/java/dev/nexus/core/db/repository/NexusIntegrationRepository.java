package dev.nexus.core.db.repository;

import dev.nexus.core.db.entity.NexusIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NexusIntegrationRepository extends JpaRepository<NexusIntegration, UUID> {
    Optional<NexusIntegration> findByName(String name);
}
