package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.AuditLog;
import lombok.Value;

import java.time.Instant;

@Value
public class AuditLogResponse {
    String id;
    String actorUserId;
    String actorEmail;
    String action;
    String targetType;
    String targetId;
    String targetName;
    String message;
    String metadataJson;
    String ipAddress;
    String userAgent;
    Instant createdAt;

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getActorEmail(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getTargetName(), log.getMessage(),
                log.getMetadataJson(), log.getIpAddress(), log.getUserAgent(), log.getCreatedAt());
    }
}
