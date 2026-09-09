package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "storage_nodes", uniqueConstraints = @UniqueConstraint(name = "uk_storage_endpoint_bucket", columnNames = {"endpoint", "bucket_name"}))
@Getter @Setter @NoArgsConstructor
public class StorageNode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 1000) private String note;
    @Column(nullable = false, length = 500) private String endpoint;
    @Column(length = 80) private String region = "us-east-1";
    @Column(name = "access_key", nullable = false, length = 180) private String accessKey;
    @Column(name = "secret_key", nullable = false, length = 300) private String secretKey;
    @Column(name = "bucket_name", nullable = false, length = 180) private String bucket;
    @Column(name = "capacity_bytes") private long capacityBytes;
    @Column(name = "bandwidth_limit_bytes") private long bandwidthLimitBytes;
    @Column(name = "bandwidth_used_bytes", nullable = false) private long bandwidthUsedBytes;
    @Column(name = "bandwidth_reset_at") private Instant bandwidthResetAt;
    @Column(name = "used_bytes", nullable = false) private long usedBytes;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false) private int priority = 0;
    @Column(name = "last_checked_at") private Instant lastCheckedAt;
    @Column(name = "last_error", length = 500) private String lastError;
}
