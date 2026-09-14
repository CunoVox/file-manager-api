package cloud.haovo.filemanager.api.response;

import cloud.haovo.filemanager.domain.BillingOrder;
import cloud.haovo.filemanager.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class BillingOrderResponse {
    private String id;
    private String userId;
    private String userEmail;
    private String userName;
    private String planName;
    private long amount;
    private String currency;
    private String status;
    private long providerOrderCode;
    private String checkoutUrl;
    private Instant paidAt;
    private Instant createdAt;

    public static BillingOrderResponse from(BillingOrder order) {
        return from(order, null);
    }

    public static BillingOrderResponse from(BillingOrder order, User user) {
        return new BillingOrderResponse(order.getId(), order.getUserId(),
                user == null ? null : user.getEmail(), user == null ? null : user.getFullName(),
                order.getPlanName(), order.getAmount(), order.getCurrency(), order.getStatus(),
                order.getProviderOrderCode(), order.getCheckoutUrl(), order.getPaidAt(), order.getCreatedAt());
    }
}
