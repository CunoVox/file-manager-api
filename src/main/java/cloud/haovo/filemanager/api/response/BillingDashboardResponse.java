package cloud.haovo.filemanager.api.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BillingDashboardResponse {
    private long plans;
    private long activeSubscriptions;
    private long paidOrders;
    private long pendingOrders;
    private long paidRevenue;
    private List<BillingOrderResponse> recentOrders;
}
