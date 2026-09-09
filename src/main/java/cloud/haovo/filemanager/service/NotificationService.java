package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.NotificationResponse;
import cloud.haovo.filemanager.domain.Notification;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserRole;
import cloud.haovo.filemanager.repository.NotificationRepository;
import cloud.haovo.filemanager.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final SmtpSettingsService smtpSettingsService;
    private final EmailTemplateService emailTemplateService;
    private final String webBaseUrl;

    public NotificationService(NotificationRepository notifications, UserRepository users,
            SmtpSettingsService smtpSettingsService, EmailTemplateService emailTemplateService,
            @Value("${app.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.notifications = notifications;
        this.users = users;
        this.smtpSettingsService = smtpSettingsService;
        this.emailTemplateService = emailTemplateService;
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    public Page<NotificationResponse> list(int page, int size) {
        User user = currentUser();
        return notifications.findByUserIdOrderByCreatedAtDesc(user.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)))
                .map(NotificationResponse::from);
    }

    public long unreadCount() {
        return notifications.countByUserIdAndReadAtIsNull(currentUser().getId());
    }

    public NotificationResponse markRead(String id) {
        User user = currentUser();
        Notification notification = notifications.findById(id)
                .filter(item -> user.getId().equals(item.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notifications.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    public void markAllRead() {
        notifications.markAllRead(currentUser().getId(), Instant.now());
    }

    public void notifyUser(String userId, String type, String title, String message,
            String targetType, String targetId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(trim(title, 160));
        notification.setMessage(trim(message, 500));
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notifications.save(notification);
        users.findById(userId).ifPresent(user -> sendNotificationEmail(user, type, title, message, targetId));
    }

    public void notifyAdmins(String type, String title, String message, String targetType, String targetId) {
        users.findAll().stream()
                .filter(user -> user.isEnabled() && user.safeRoles().contains(UserRole.ADMIN))
                .collect(Collectors.toList())
                .forEach(user -> notifyUser(user.getId(), type, title, message, targetType, targetId));
    }

    public void sendFileSharedEmail(User target, User owner, String fileName, String fileId) {
        Map<String, String> variables = new HashMap<>();
        variables.put("fullName", target.getFullName());
        variables.put("email", target.getEmail());
        variables.put("ownerName", owner.getFullName());
        variables.put("ownerEmail", owner.getEmail());
        variables.put("fileName", fileName);
        variables.put("fileLink", webBaseUrl + "/shared");
        EmailTemplateService.RenderedEmail email = emailTemplateService.render(EmailTemplateService.FILE_SHARED, variables);
        trySend(target.getEmail(), email);
    }

    private void sendNotificationEmail(User user, String type, String title, String message, String targetId) {
        if (!isAdminAlert(type)) {
            return;
        }
        EmailTemplateService.RenderedEmail email = emailTemplateService.render(EmailTemplateService.ADMIN_ALERT,
                Map.of("title", title, "message", message, "time", DateTimeFormatter.ISO_INSTANT.format(Instant.now())));
        trySend(user.getEmail(), email);
    }

    private void trySend(String to, EmailTemplateService.RenderedEmail email) {
        try {
            smtpSettingsService.queueEmail(to, email.getSubject(), email.getHtmlBody(), email.getTextBody());
        } catch (RuntimeException exception) {
            log.warn("Notification email could not be sent to {}", to, exception);
        }
    }

    private boolean isAdminAlert(String type) {
        return type != null && (type.startsWith("STORAGE_NODE_") || type.startsWith("STORAGE_MIGRATION_"));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authentication is required");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private String trim(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
