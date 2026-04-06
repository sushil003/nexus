package dev.nexus.app.schedule;

import dev.nexus.core.db.repository.NexusEventRepository;
import dev.nexus.core.db.repository.NexusPermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);
    private static final int EVENT_RETENTION_DAYS = 30;

    private final NexusPermissionRepository permissionRepo;
    private final NexusEventRepository eventRepo;

    public CleanupScheduler(NexusPermissionRepository permissionRepo,
                            NexusEventRepository eventRepo) {
        this.permissionRepo = permissionRepo;
        this.eventRepo = eventRepo;
    }

    @Scheduled(fixedRate = 60_000) // every minute
    @Transactional
    public void expirePermissions() {
        int expired = permissionRepo.expireOldPermissions(Instant.now());
        if (expired > 0) {
            log.info("Expired {} pending permissions", expired);
        }
    }

    @Scheduled(fixedRate = 86_400_000) // daily
    @Transactional
    public void purgeOldEvents() {
        Instant cutoff = Instant.now().minus(EVENT_RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = eventRepo.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} events older than {} days", deleted, EVENT_RETENTION_DAYS);
        }
    }
}
