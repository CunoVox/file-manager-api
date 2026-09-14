package cloud.haovo.filemanager.api.response;

import cloud.haovo.filemanager.domain.BillingPlan;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BillingPlanResponse {
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
