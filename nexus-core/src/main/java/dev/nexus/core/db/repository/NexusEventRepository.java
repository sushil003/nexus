package dev.nexus.core.db.repository;

import dev.nexus.core.db.entity.NexusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NexusEventRepository extends JpaRepository<NexusEvent, UUID> {
}
