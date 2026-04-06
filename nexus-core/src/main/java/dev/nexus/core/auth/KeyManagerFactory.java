package dev.nexus.core.auth;

import dev.nexus.core.config.NexusProperties;
import dev.nexus.core.db.entity.NexusAccount;
import dev.nexus.core.db.repository.NexusAccountRepository;
import org.springframework.stereotype.Component;

@Component
public class KeyManagerFactory {

    private final NexusAccountRepository accountRepo;
    private final EncryptionService encryptionService;
    private final String kek;

    public KeyManagerFactory(NexusAccountRepository accountRepo,
                             EncryptionService encryptionService,
                             NexusProperties properties) {
        this.accountRepo = accountRepo;
        this.encryptionService = encryptionService;
        this.kek = properties.kek();
    }

    public KeyManager create(NexusAccount account) {
        return new DefaultKeyManager(account, accountRepo, encryptionService, kek);
    }

    public KeyManager createForPlugin(String pluginId, String tenantId) {
        NexusAccount account = accountRepo.findByTenantIdAndIntegration_Name(tenantId, pluginId)
                .orElseThrow(() -> new IllegalStateException(
                        "No account found for plugin '%s' and tenant '%s'".formatted(pluginId, tenantId)));
        return create(account);
    }
}
