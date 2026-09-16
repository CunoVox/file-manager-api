package cloud.haovo.filemanager.api.controller;

import cloud.haovo.filemanager.api.request.AssignPlanRequest;
import cloud.haovo.filemanager.api.request.BillingPlanRequest;
import cloud.haovo.filemanager.api.request.CheckoutRequest;
import cloud.haovo.filemanager.api.request.PayosSettingsRequest;
import cloud.haovo.filemanager.api.response.BillingDashboardResponse;
import cloud.haovo.filemanager.api.response.BillingOrderResponse;
import cloud.haovo.filemanager.api.response.BillingPlanResponse;
import cloud.haovo.filemanager.api.response.CheckoutResponse;
import cloud.haovo.filemanager.api.response.PayosSettingsResponse;
import cloud.haovo.filemanager.api.response.UserSubscriptionResponse;
import cloud.haovo.filemanager.api.response.WebhookLogResponse;
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
    public List<BillingPlanResponse> plans() {
        return billingService.publicPlans();
    }

    @PostMapping("/billing/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return billingService.checkout(request.getPlanId());
    }

    @GetMapping("/billing/orders")
    public Page<BillingOrderResponse> myOrders(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return billingService.myOrders(page, size);
    }

    @PostMapping("/billing/orders/{id}/cancel")
    public BillingOrderResponse cancelOrder(@PathVariable String id) {
        return billingService.cancelOrder(id);
    }

    @GetMapping("/billing/subscriptions")
    public List<UserSubscriptionResponse> mySubscriptions() {
        return billingService.mySubscriptions();
    }

    @PostMapping("/payments/payos/webhook")
    public void payosWebhook(@RequestBody JsonNode payload) {
        billingService.handlePayosWebhook(payload);
    }

    @GetMapping("/admin/billing/payos")
    @PreAuthorize("hasRole('ADMIN')")
    public PayosSettingsResponse payosSettings() {
        return billingService.payosSettings();
    }

    @GetMapping("/admin/billing/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDashboardResponse dashboard() {
        return billingService.dashboard();
    }

    @GetMapping("/admin/billing/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<BillingOrderResponse> adminOrders(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return billingService.adminOrders(status, page, size);
    }

    @GetMapping("/admin/billing/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserSubscriptionResponse> adminSubscriptions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return billingService.adminSubscriptions(page, size);
    }

    @GetMapping("/admin/billing/users/{userId}/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSubscriptionResponse> userSubscriptions(@PathVariable String userId) {
        return billingService.subscriptionsForUser(userId);
    }

    @PostMapping("/admin/billing/subscriptions/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSubscriptionResponse assignPlan(@Valid @RequestBody AssignPlanRequest request) {
        return billingService.assignPlan(request);
    }

    @GetMapping("/admin/billing/webhook-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<WebhookLogResponse> webhookLogs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return billingService.webhookLogs(page, size);
    }

    @PatchMapping("/admin/billing/payos")
    @PreAuthorize("hasRole('ADMIN')")
    public PayosSettingsResponse updatePayos(@RequestBody PayosSettingsRequest request) {
        return billingService.updatePayosSettings(request);
    }

    @GetMapping("/admin/billing/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BillingPlanResponse> adminPlans() {
        return billingService.adminPlans();
    }

    @PostMapping("/admin/billing/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingPlanResponse createPlan(@Valid @RequestBody BillingPlanRequest request) {
        return billingService.createPlan(request);
    }

    @PatchMapping("/admin/billing/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingPlanResponse updatePlan(@PathVariable String id,
            @Valid @RequestBody BillingPlanRequest request) {
        return billingService.updatePlan(id, request);
    }
}
