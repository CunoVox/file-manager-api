package cloud.haovo.filemanager.api.response;

import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserSubscription;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class UserSubscriptionResponse {
    private String id;
    private String userId;
    private String userEmail;
    private String userName;
    private String planId;
    private String planName;
    private long quotaBytes;
    private Instant startsAt;
    private Instant expiresAt;
    private boolean active;

    public static UserSubscriptionResponse from(UserSubscription subscription, User user) {
        return new UserSubscriptionResponse(subscription.getId(), subscription.getUserId(),
                user == null ? null : user.getEmail(), user == null ? null : user.getFullName(),
                subscription.getPlanId(), subscription.getPlanName(), subscription.getQuotaBytes(),
                subscription.getStartsAt(), subscription.getExpiresAt(), subscription.isActive());
    }
}
