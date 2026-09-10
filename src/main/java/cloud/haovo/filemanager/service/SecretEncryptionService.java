package cloud.haovo.filemanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class SecretEncryptionService {
    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String encryptionKey;

    public SecretEncryptionService(@Value("${app.encryption-key:}") String encryptionKey) {
        this.encryptionKey = encryptionKey == null ? "" : encryptionKey.trim();
    }

    public String encrypt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        SecretKeySpec key = key();
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt secret", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        SecretKeySpec key = key();
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt secret", exception);
        }
    }

    public boolean isConfigured() {
        return !encryptionKey.isBlank();
    }

    private SecretKeySpec key() {
        if (encryptionKey.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY is required to store payment secrets");
        }
        try {
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(encryptionKey);
            } catch (IllegalArgumentException ignored) {
                raw = encryptionKey.getBytes(StandardCharsets.UTF_8);
            }
            byte[] keyBytes = raw.length == 32 ? raw : MessageDigest.getInstance("SHA-256").digest(raw);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid APP_ENCRYPTION_KEY", exception);
        }
    }
}
