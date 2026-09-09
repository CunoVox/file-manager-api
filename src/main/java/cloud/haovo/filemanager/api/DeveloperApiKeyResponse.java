package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.DeveloperApiKey;
import lombok.Value;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Value
public class DeveloperApiKeyResponse {
    String id;
    String name;
    String prefix;
    List<String> scopes;
    boolean active;
    Instant expiresAt;
    Instant lastUsedAt;
    Instant createdAt;
    String token;

    public static DeveloperApiKeyResponse from(DeveloperApiKey key) {
        return from(key, null);
    }

    public static DeveloperApiKeyResponse from(DeveloperApiKey key, String token) {
        return new DeveloperApiKeyResponse(key.getId(), key.getName(), key.getPrefix(), splitScopes(key.getScopes()),
                key.isActive(), key.getExpiresAt(), key.getLastUsedAt(), key.getCreatedAt(), token);
    }

    private static List<String> splitScopes(String scopes) {
        if (scopes == null || scopes.trim().isEmpty()) return List.of();
        return Arrays.stream(scopes.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }
}
