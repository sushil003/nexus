package dev.nexus.app.setup;

import dev.nexus.core.auth.EncryptionService;
import dev.nexus.core.config.NexusProperties;
import dev.nexus.core.db.entity.NexusAccount;
import dev.nexus.core.db.entity.NexusIntegration;
import dev.nexus.core.db.repository.NexusAccountRepository;
import dev.nexus.core.db.repository.NexusIntegrationRepository;
import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class NexusInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NexusInitializer.class);

    private final PluginRegistry pluginRegistry;
    private final NexusIntegrationRepository integrationRepo;
    private final NexusAccountRepository accountRepo;
    private final EncryptionService encryptionService;
    private final NexusProperties properties;

    public NexusInitializer(PluginRegistry pluginRegistry,
                            NexusIntegrationRepository integrationRepo,
                            NexusAccountRepository accountRepo,
                            EncryptionService encryptionService,
                            NexusProperties properties) {
        this.pluginRegistry = pluginRegistry;
        this.integrationRepo = integrationRepo;
        this.accountRepo = accountRepo;
        this.encryptionService = encryptionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateKek();
        bootstrapPlugins();
    }

    private void validateKek() {
        String kek = properties.kek();
        if (kek == null || kek.isBlank()) {
            throw new IllegalStateException(
                    "NEXUS_KEK must be set. Provide it via environment variable or nexus.kek property.");
        }

        // Verify KEK can decrypt existing data
        integrationRepo.findAll().stream()
                .filter(i -> i.getDek() != null)
                .findFirst()
                .ifPresent(integration -> {
                    try {
                        encryptionService.decryptDEK(integration.getDek(), kek);
                    } catch (Exception e) {
                        throw new IllegalStateException(
                                "NEXUS_KEK does not match the key used to encrypt existing data. " +
                                "Restore the original KEK or run the KEK rotation procedure.", e);
                    }
                });

        log.info("KEK validation passed");
    }

    private void bootstrapPlugins() {
        String kek = properties.kek();

        for (NexusPlugin plugin : pluginRegistry.getAll().values()) {
            String pluginId = plugin.getId();

            NexusIntegration integration = integrationRepo.findByName(pluginId)
                    .orElseGet(() -> {
                        log.info("Creating integration for plugin: {}", pluginId);
                        NexusIntegration newIntegration = new NexusIntegration();
                        newIntegration.setName(pluginId);
                        return integrationRepo.save(newIntegration);
                    });

            if (integration.getDek() == null) {
                String dek = encryptionService.generateDEK();
                String encryptedDek = encryptionService.encryptDEK(dek, kek);
                integration.setDek(encryptedDek);
                integrationRepo.save(integration);
                log.info("Generated DEK for integration: {}", pluginId);
            }

            accountRepo.findByTenantIdAndIntegration_Name("default", pluginId)
                    .orElseGet(() -> {
                        log.info("Creating default account for plugin: {}", pluginId);
                        NexusAccount account = new NexusAccount();
                        account.setTenantId("default");
                        account.setIntegration(integration);
                        String dek = encryptionService.generateDEK();
                        account.setDek(encryptionService.encryptDEK(dek, kek));
                        return accountRepo.save(account);
                    });
        }

        log.info("Bootstrapped {} plugins", pluginRegistry.getAll().size());
    }
}
