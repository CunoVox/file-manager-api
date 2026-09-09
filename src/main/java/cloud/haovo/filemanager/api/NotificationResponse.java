package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.Notification;
import lombok.Value;

import java.time.Instant;

@Value
public class NotificationResponse {
    String id;
    String type;
    String title;
    String message;
    String targetType;
    String targetId;
    Instant readAt;
    Instant createdAt;

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getTargetType(), notification.getTargetId(),
                notification.getReadAt(), notification.getCreatedAt());
    }
}
