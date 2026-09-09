package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "developer_api_keys", indexes = {
        @Index(name = "idx_api_keys_owner", columnList = "owner_id"),
        @Index(name = "idx_api_keys_hash", columnList = "key_hash", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class DeveloperApiKey {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 24)
    private String prefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "scopes", nullable = false, length = 500)
    private String scopes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
