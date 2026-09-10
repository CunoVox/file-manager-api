package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.BillingOrder;
import cloud.haovo.filemanager.domain.BillingPlan;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Instant;

public class BillingDtos {
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PayosSettingsRequest {
        private boolean enabled;
        private String clientId;
        private String apiKey;
        private String checksumKey;
        private String returnUrl;
        private String cancelUrl;
    }

    @Getter
    @AllArgsConstructor
    public static class PayosSettingsResponse {
        private boolean enabled;
        private String clientId;
        private boolean apiKeyConfigured;
        private boolean checksumKeyConfigured;
        private String returnUrl;
        private String cancelUrl;
        private String webhookUrl;
        private boolean encryptionConfigured;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BillingPlanRequest {
        @NotBlank
        private String name;
        @Min(1)
        private long quotaGb;
        @Min(0)
        private long price;
        private String currency = "VND";
        @Min(0)
        private int durationDays = 30;
        private String description;
        private boolean active = true;
        private int sortOrder;
    }

    @Getter
    @AllArgsConstructor
    public static class BillingPlanResponse {
        private String id;
        private String name;
        private long quotaBytes;
        private long quotaGb;
        private long price;
        private String currency;
        private int durationDays;
        private String description;
        private boolean active;
        private int sortOrder;

        public static BillingPlanResponse from(BillingPlan plan) {
            return new BillingPlanResponse(plan.getId(), plan.getName(), plan.getQuotaBytes(),
                    plan.getQuotaBytes() / (1024L * 1024 * 1024), plan.getPrice(), plan.getCurrency(),
                    plan.getDurationDays(), plan.getDescription(), plan.isActive(), plan.getSortOrder());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CheckoutRequest {
        @NotNull
        private String planId;
    }

    @Getter
    @AllArgsConstructor
    public static class CheckoutResponse {
        private String orderId;
        private long orderCode;
        private String checkoutUrl;
    }

    @Getter
    @AllArgsConstructor
    public static class BillingOrderResponse {
        private String id;
        private String planName;
        private long amount;
        private String currency;
        private String status;
        private String checkoutUrl;
        private Instant paidAt;
        private Instant createdAt;

        public static BillingOrderResponse from(BillingOrder order) {
            return new BillingOrderResponse(order.getId(), order.getPlanName(), order.getAmount(),
                    order.getCurrency(), order.getStatus(), order.getCheckoutUrl(), order.getPaidAt(), order.getCreatedAt());
        }
    }
}
