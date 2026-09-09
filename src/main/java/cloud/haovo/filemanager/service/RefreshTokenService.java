package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.RefreshToken;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokens;
    private final long expirationMillis;

    public RefreshTokenService(RefreshTokenRepository refreshTokens,
            @Value("${app.security.refresh-expiration-ms:2592000000}") long expirationMillis) {
        this.refreshTokens = refreshTokens;
        this.expirationMillis = expirationMillis;
    }

    public String create(User user) {
        String rawToken = UUID.randomUUID().toString() + "." + UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plusMillis(expirationMillis));
        refreshTokens.save(token);
        return rawToken;
    }

    public User consume(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHashAndRevokedFalse(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokens.save(token);
            throw new IllegalArgumentException("Refresh token is expired");
        }
        token.setRevoked(true);
        refreshTokens.save(token);
        return token.getUser();
    }

    public void revoke(String rawToken) {
        refreshTokens.findByTokenHashAndRevokedFalse(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokens.save(token);
        });
    }

    public void revokeAll(User user) {
        refreshTokens.revokeAllByUserId(user.getId());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash refresh token", exception);
        }
    }
}
