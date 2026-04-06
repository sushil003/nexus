package dev.nexus.core.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class EncryptionService {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int AUTH_TAG_BITS = 128;
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final String KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateDEK() {
        byte[] dek = new byte[32];
        secureRandom.nextBytes(dek);
        return Base64.getEncoder().encodeToString(dek);
    }

    public String encryptDEK(String dek, String kek) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);

            byte[] derivedKey = deriveKey(kek, salt);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(derivedKey, "AES"),
                    new GCMParameterSpec(AUTH_TAG_BITS, iv));

            byte[] encrypted = cipher.doFinal(dek.getBytes());

            // GCM appends auth tag to ciphertext — split them
            int ciphertextLen = encrypted.length - (AUTH_TAG_BITS / 8);
            byte[] ciphertext = new byte[ciphertextLen];
            byte[] authTag = new byte[AUTH_TAG_BITS / 8];
            System.arraycopy(encrypted, 0, ciphertext, 0, ciphertextLen);
            System.arraycopy(encrypted, ciphertextLen, authTag, 0, authTag.length);

            return encode(salt) + ":" + encode(iv) + ":" + encode(authTag) + ":" + encode(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt DEK", e);
        }
    }

    public String decryptDEK(String encryptedDek, String kek) {
        try {
            String[] parts = encryptedDek.split(":");
            if (parts.length != 4) throw new IllegalArgumentException("Invalid encrypted DEK format");

            byte[] salt = decode(parts[0]);
            byte[] iv = decode(parts[1]);
            byte[] authTag = decode(parts[2]);
            byte[] ciphertext = decode(parts[3]);

            byte[] derivedKey = deriveKey(kek, salt);

            // Reconstruct GCM ciphertext (ciphertext + authTag)
            byte[] combined = new byte[ciphertext.length + authTag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(authTag, 0, combined, ciphertext.length, authTag.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(derivedKey, "AES"),
                    new GCMParameterSpec(AUTH_TAG_BITS, iv));

            return new String(cipher.doFinal(combined));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt DEK", e);
        }
    }

    public String encryptValue(String value, String dek) {
        try {
            byte[] dekBytes = Base64.getDecoder().decode(dek);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(dekBytes, "AES"),
                    new GCMParameterSpec(AUTH_TAG_BITS, iv));

            byte[] encrypted = cipher.doFinal(value.getBytes());

            int ciphertextLen = encrypted.length - (AUTH_TAG_BITS / 8);
            byte[] ciphertext = new byte[ciphertextLen];
            byte[] authTag = new byte[AUTH_TAG_BITS / 8];
            System.arraycopy(encrypted, 0, ciphertext, 0, ciphertextLen);
            System.arraycopy(encrypted, ciphertextLen, authTag, 0, authTag.length);

            return encode(iv) + ":" + encode(authTag) + ":" + encode(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    public String decryptValue(String encryptedValue, String dek) {
        try {
            String[] parts = encryptedValue.split(":");
            if (parts.length != 3) throw new IllegalArgumentException("Invalid encrypted value format");

            byte[] iv = decode(parts[0]);
            byte[] authTag = decode(parts[1]);
            byte[] ciphertext = decode(parts[2]);
            byte[] dekBytes = Base64.getDecoder().decode(dek);

            byte[] combined = new byte[ciphertext.length + authTag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(authTag, 0, combined, ciphertext.length, authTag.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(dekBytes, "AES"),
                    new GCMParameterSpec(AUTH_TAG_BITS, iv));

            return new String(cipher.doFinal(combined));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    public Map<String, String> encryptConfig(Map<String, String> config, String dek) {
        Map<String, String> encrypted = new HashMap<>();
        config.forEach((key, value) -> encrypted.put(key, encryptValue(value, dek)));
        return encrypted;
    }

    public Map<String, String> decryptConfig(Map<String, String> encryptedConfig, String dek) {
        Map<String, String> decrypted = new HashMap<>();
        encryptedConfig.forEach((key, value) -> decrypted.put(key, decryptValue(value, dek)));
        return decrypted;
    }

    private byte[] deriveKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO);
        return factory.generateSecret(spec).getEncoded();
    }

    private String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }
}
