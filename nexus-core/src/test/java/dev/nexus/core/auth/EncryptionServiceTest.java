package dev.nexus.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
    }

    @Test
    void generateDEK_produces32ByteBase64() {
        String dek = encryptionService.generateDEK();
        assertNotNull(dek);
        byte[] decoded = java.util.Base64.getDecoder().decode(dek);
        assertEquals(32, decoded.length);
    }

    @Test
    void encryptAndDecryptDEK_roundTrip() {
        String kek = "my-super-secret-master-key-12345";
        String dek = encryptionService.generateDEK();

        String encrypted = encryptionService.encryptDEK(dek, kek);
        assertNotNull(encrypted);
        assertTrue(encrypted.contains(":"));

        String[] parts = encrypted.split(":");
        assertEquals(4, parts.length, "Format should be salt:iv:authTag:data");

        String decrypted = encryptionService.decryptDEK(encrypted, kek);
        assertEquals(dek, decrypted);
    }

    @Test
    void decryptDEK_withWrongKey_throws() {
        String kek = "correct-key";
        String wrongKek = "wrong-key";
        String dek = encryptionService.generateDEK();

        String encrypted = encryptionService.encryptDEK(dek, kek);
        assertThrows(RuntimeException.class, () -> encryptionService.decryptDEK(encrypted, wrongKek));
    }

    @Test
    void encryptAndDecryptValue_roundTrip() {
        String dek = encryptionService.generateDEK();
        String value = "ghp_abc123_my_api_key";

        String encrypted = encryptionService.encryptValue(value, dek);
        assertNotNull(encrypted);

        String[] parts = encrypted.split(":");
        assertEquals(3, parts.length, "Format should be iv:authTag:data");

        String decrypted = encryptionService.decryptValue(encrypted, dek);
        assertEquals(value, decrypted);
    }

    @Test
    void encryptAndDecryptConfig_roundTrip() {
        String dek = encryptionService.generateDEK();
        Map<String, String> config = Map.of(
                "api_key", "ghp_abc123",
                "webhook_secret", "whsec_xyz789"
        );

        Map<String, String> encrypted = encryptionService.encryptConfig(config, dek);
        assertEquals(2, encrypted.size());
        assertNotEquals(config.get("api_key"), encrypted.get("api_key"));

        Map<String, String> decrypted = encryptionService.decryptConfig(encrypted, dek);
        assertEquals(config, decrypted);
    }

    @Test
    void differentEncryptions_produceDifferentCiphertexts() {
        String dek = encryptionService.generateDEK();
        String value = "same-value";

        String encrypted1 = encryptionService.encryptValue(value, dek);
        String encrypted2 = encryptionService.encryptValue(value, dek);

        assertNotEquals(encrypted1, encrypted2, "Different IVs should produce different ciphertexts");

        assertEquals(value, encryptionService.decryptValue(encrypted1, dek));
        assertEquals(value, encryptionService.decryptValue(encrypted2, dek));
    }
}
