package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.BillingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/billing/plans")
    public List<BillingDtos.BillingPlanResponse> plans() {
        return billingService.publicPlans();
    }

    @PostMapping("/billing/checkout")
    public BillingDtos.CheckoutResponse checkout(@Valid @RequestBody BillingDtos.CheckoutRequest request) {
        return billingService.checkout(request.getPlanId());
    }

    @GetMapping("/billing/orders")
    public Page<BillingDtos.BillingOrderResponse> myOrders(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return billingService.myOrders(page, size);
    }

    @PostMapping("/payments/payos/webhook")
    public void payosWebhook(@RequestBody JsonNode payload) {
        billingService.handlePayosWebhook(payload);
    }

    @GetMapping("/admin/billing/payos")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDtos.PayosSettingsResponse payosSettings() {
        return billingService.payosSettings();
    }

    @PatchMapping("/admin/billing/payos")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDtos.PayosSettingsResponse updatePayos(@RequestBody BillingDtos.PayosSettingsRequest request) {
        return billingService.updatePayosSettings(request);
    }

    @GetMapping("/admin/billing/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BillingDtos.BillingPlanResponse> adminPlans() {
        return billingService.adminPlans();
    }

    @PostMapping("/admin/billing/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDtos.BillingPlanResponse createPlan(@Valid @RequestBody BillingDtos.BillingPlanRequest request) {
        return billingService.createPlan(request);
    }

    @PatchMapping("/admin/billing/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDtos.BillingPlanResponse updatePlan(@PathVariable String id,
            @Valid @RequestBody BillingDtos.BillingPlanRequest request) {
        return billingService.updatePlan(id, request);
    }
}
