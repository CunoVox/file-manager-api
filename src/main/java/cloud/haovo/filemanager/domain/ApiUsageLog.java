package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_usage_logs", indexes = {
        @Index(name = "idx_api_usage_owner", columnList = "owner_id"),
        @Index(name = "idx_api_usage_key", columnList = "api_key_id"),
        @Index(name = "idx_api_usage_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ApiUsageLog {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "api_key_id", nullable = false, length = 36)
    private String apiKeyId;

    @Column(name = "api_key_name", nullable = false, length = 120)
    private String apiKeyName;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private int status;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
