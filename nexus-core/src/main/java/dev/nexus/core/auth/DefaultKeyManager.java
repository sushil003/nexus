package dev.nexus.core.auth;

import dev.nexus.core.db.entity.NexusAccount;
import dev.nexus.core.db.repository.NexusAccountRepository;

import java.util.Map;
import java.util.Optional;

public class DefaultKeyManager implements KeyManager {

    private final NexusAccount account;
    private final NexusAccountRepository accountRepo;
    private final EncryptionService encryptionService;
    private final String kek;

    public DefaultKeyManager(NexusAccount account,
                             NexusAccountRepository accountRepo,
                             EncryptionService encryptionService,
                             String kek) {
        this.account = account;
        this.accountRepo = accountRepo;
        this.encryptionService = encryptionService;
        this.kek = kek;
    }

    @Override
    public Optional<String> getField(String name) {
        Map<String, Object> config = account.getConfig();
        if (config == null || !config.containsKey(name)) {
            return Optional.empty();
        }
        String encryptedValue = (String) config.get(name);
        String dek = decryptAccountDek();
        return Optional.of(encryptionService.decryptValue(encryptedValue, dek));
    }

    @Override
    public void setField(String name, String value) {
        String dek = decryptAccountDek();
        String encryptedValue = encryptionService.encryptValue(value, dek);

        Map<String, Object> config = account.getConfig();
        if (config == null) {
            config = new java.util.HashMap<>();
        } else {
            config = new java.util.HashMap<>(config);
        }
        config.put(name, encryptedValue);
        account.setConfig(config);
        accountRepo.save(account);
    }

    private String decryptAccountDek() {
        return encryptionService.decryptDEK(account.getDek(), kek);
    }
}
