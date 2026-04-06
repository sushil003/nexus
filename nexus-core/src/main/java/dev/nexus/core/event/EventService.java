package dev.nexus.core.event;

import dev.nexus.core.db.entity.NexusAccount;
import dev.nexus.core.db.entity.NexusEvent;
import dev.nexus.core.db.repository.NexusEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final NexusEventRepository eventRepo;

    public EventService(NexusEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    public void logEvent(NexusAccount account, String eventType, Map<String, Object> payload, String status) {
        NexusEvent event = new NexusEvent();
        event.setAccount(account);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setStatus(status);
        eventRepo.save(event);
        log.debug("Logged event: type={}, status={}, account={}", eventType, status, account.getId());
    }

    public void logSuccess(NexusAccount account, String endpoint, Map<String, Object> payload) {
        logEvent(account, endpoint, payload, "SUCCESS");
    }

    public void logFailure(NexusAccount account, String endpoint, Map<String, Object> payload) {
        logEvent(account, endpoint, payload, "FAILURE");
    }
}
