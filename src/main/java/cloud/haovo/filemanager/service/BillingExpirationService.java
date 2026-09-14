package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserSubscription;
import cloud.haovo.filemanager.repository.NotificationRepository;
import cloud.haovo.filemanager.repository.UserRepository;
import cloud.haovo.filemanager.repository.UserSubscriptionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class BillingExpirationService {
    private final UserSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public BillingExpirationService(UserSubscriptionRepository subscriptions, UserRepository users,
            NotificationRepository notifications, AuditLogService auditLogService, NotificationService notificationService) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.notifications = notifications;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${app.billing.expiration-cron:0 0 * * * *}")
    @Transactional
    public void expireSubscriptions() {
        for (UserSubscription subscription : subscriptions.findByActiveTrueAndExpiresAtBefore(Instant.now())) {
            subscription.setActive(false);
            subscriptions.save(subscription);
            User user = users.findById(subscription.getUserId()).orElse(null);
            if (user == null) continue;
            user.setStorageQuotaBytes(null);
            users.save(user);
            auditLogService.record("BILLING_SUBSCRIPTION_EXPIRED", "USER_SUBSCRIPTION", subscription.getId(),
                    subscription.getPlanName(), "Expired billing subscription " + subscription.getPlanName(),
                    Map.of("userId", user.getId()));
            notificationService.notifyUser(user.getId(), "BILLING_SUBSCRIPTION_EXPIRED", "Storage plan expired",
                    "Your HaoBox storage plan expired and your account returned to the free quota.",
                    "USER_SUBSCRIPTION", subscription.getId());
        }
    }

    @Scheduled(cron = "${app.billing.reminder-cron:0 15 * * * *}")
    @Transactional
    public void remindExpiringSubscriptions() {
        Instant now = Instant.now();
        Instant soon = now.plusSeconds(3 * 86400L);
        for (UserSubscription subscription : subscriptions.findByActiveTrueAndExpiresAtBetween(now, soon)) {
            User user = users.findById(subscription.getUserId()).orElse(null);
            if (user == null) continue;
            String type = "BILLING_SUBSCRIPTION_EXPIRING";
            if (notifications.existsByUserIdAndTypeAndTargetId(user.getId(), type, subscription.getId())) continue;
            notificationService.notifyUser(user.getId(), type, "Storage plan expiring soon",
                    "Your " + subscription.getPlanName() + " plan expires soon. Renew to keep your current quota.",
                    "USER_SUBSCRIPTION", subscription.getId());
        }
    }
}

