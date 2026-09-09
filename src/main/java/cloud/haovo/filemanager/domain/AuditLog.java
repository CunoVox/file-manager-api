package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_logs_action", columnList = "action"),
        @Index(name = "idx_audit_logs_actor", columnList = "actor_user_id"),
        @Index(name = "idx_audit_logs_target", columnList = "target_type,target_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "actor_user_id", length = 36)
    private String actorUserId;

    @Column(name = "actor_email", length = 180)
    private String actorEmail;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", length = 80)
    private String targetId;

    @Column(name = "target_name", length = 255)
    private String targetName;

    @Column(nullable = false, length = 500)
    private String message;

    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
