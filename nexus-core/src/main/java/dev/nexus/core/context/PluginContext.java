package dev.nexus.core.context;

import dev.nexus.core.auth.KeyManager;
import dev.nexus.core.db.repository.NexusEntityRepository;
import dev.nexus.core.http.NexusHttpClient;

public record PluginContext(
        NexusHttpClient httpClient,
        KeyManager keyManager,
        String tenantId,
        NexusEntityRepository entityRepo
) {
}
