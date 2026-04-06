package dev.nexus.core.db.repository;

import dev.nexus.core.db.entity.NexusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface NexusEventRepository extends JpaRepository<NexusEvent, UUID> {

    @Modifying
    @Query("DELETE FROM NexusEvent e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
