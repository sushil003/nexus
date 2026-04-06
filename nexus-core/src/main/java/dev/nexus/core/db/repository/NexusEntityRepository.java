package dev.nexus.core.db.repository;

import dev.nexus.core.db.entity.NexusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NexusEntityRepository extends JpaRepository<NexusEntity, UUID> {
    Optional<NexusEntity> findByAccount_IdAndEntityTypeAndEntityId(UUID accountId, String entityType, String entityId);

    List<NexusEntity> findByAccount_IdAndEntityType(UUID accountId, String entityType);
}
