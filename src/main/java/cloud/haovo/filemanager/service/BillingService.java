package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.BillingDtos;
import cloud.haovo.filemanager.domain.AppSetting;
import cloud.haovo.filemanager.domain.BillingOrder;
import cloud.haovo.filemanager.domain.BillingPlan;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserSubscription;
import cloud.haovo.filemanager.repository.AppSettingRepository;
import cloud.haovo.filemanager.repository.BillingOrderRepository;
import cloud.haovo.filemanager.repository.BillingPlanRepository;
import cloud.haovo.filemanager.repository.UserRepository;
import cloud.haovo.filemanager.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BillingService {
    private static final String PREFIX = "payos.";
    private static final String ENABLED = PREFIX + "enabled";
    private static final String CLIENT_ID = PREFIX + "clientId";
    private static final String API_KEY = PREFIX + "apiKey";
    private static final String CHECKSUM_KEY = PREFIX + "checksumKey";
    private static final String RETURN_URL = PREFIX + "returnUrl";
    private static final String CANCEL_URL = PREFIX + "cancelUrl";
    private static final long GB = 1024L * 1024 * 1024;

    private final AppSettingRepository settings;
    private final BillingPlanRepository plans;
    private final BillingOrderRepository orders;
    private final UserSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final SecretEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String publicBaseUrl;
    private final String webBaseUrl;

    public BillingService(AppSettingRepository settings, BillingPlanRepository plans, BillingOrderRepository orders,
            UserSubscriptionRepository subscriptions, UserRepository users, AuditLogService auditLogService,
            NotificationService notificationService, SecretEncryptionService encryptionService, ObjectMapper objectMapper,
            @Value("${app.public-base-url}") String publicBaseUrl, @Value("${app.web-base-url}") String webBaseUrl) {
        this.settings = settings;
        this.plans = plans;
        this.orders = orders;
        this.subscriptions = subscriptions;
        this.users = users;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl;
        this.webBaseUrl = webBaseUrl;
    }

    @Transactional(readOnly = true)
    public BillingDtos.PayosSettingsResponse payosSettings() {
        return new BillingDtos.PayosSettingsResponse(bool(ENABLED, false), value(CLIENT_ID, ""),
                hasText(value(API_KEY, "")), hasText(value(CHECKSUM_KEY, "")),
                value(RETURN_URL, webBaseUrl + "/billing/success"),
                value(CANCEL_URL, webBaseUrl + "/billing/cancel"),
                publicBaseUrl + "/api/v1/payments/payos/webhook",
                encryptionService.isConfigured());
    }

    @Transactional
    public BillingDtos.PayosSettingsResponse updatePayosSettings(BillingDtos.PayosSettingsRequest request) {
        if ((hasText(request.getApiKey()) || hasText(request.getChecksumKey())) && !encryptionService.isConfigured()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY is required to store PayOS secrets");
        }
        boolean hasApiKey = hasText(request.getApiKey()) || hasText(secret(API_KEY));
        boolean hasChecksumKey = hasText(request.getChecksumKey()) || hasText(secret(CHECKSUM_KEY));
        if (request.isEnabled() && (!hasText(request.getClientId()) || !hasApiKey || !hasChecksumKey)) {
            throw new IllegalArgumentException("PayOS Client ID, API Key, and Checksum Key are required");
        }
        save(ENABLED, String.valueOf(request.isEnabled()));
        save(CLIENT_ID, clean(request.getClientId()));
        if (hasText(request.getApiKey())) save(API_KEY, encryptionService.encrypt(request.getApiKey().trim()));
        if (hasText(request.getChecksumKey())) save(CHECKSUM_KEY, encryptionService.encrypt(request.getChecksumKey().trim()));
        save(RETURN_URL, hasText(request.getReturnUrl()) ? request.getReturnUrl().trim() : webBaseUrl + "/billing/success");
        save(CANCEL_URL, hasText(request.getCancelUrl()) ? request.getCancelUrl().trim() : webBaseUrl + "/billing/cancel");
        auditLogService.record("PAYOS_SETTINGS_UPDATED", "SETTING", "payos", "PayOS Settings",
                "Updated PayOS settings", Map.of("enabled", request.isEnabled()));
        return payosSettings();
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.BillingPlanResponse> publicPlans() {
        return plans.findByActiveTrueOrderBySortOrderAscCreatedAtAsc().stream()
                .map(BillingDtos.BillingPlanResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.BillingPlanResponse> adminPlans() {
        return plans.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
                .map(BillingDtos.BillingPlanResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public BillingDtos.BillingPlanResponse createPlan(BillingDtos.BillingPlanRequest request) {
        BillingPlan plan = new BillingPlan();
        apply(plan, request);
        BillingPlan saved = plans.save(plan);
        auditLogService.record("BILLING_PLAN_CREATED", "BILLING_PLAN", saved.getId(), saved.getName(),
                "Created quota plan " + saved.getName(), Map.of("price", saved.getPrice(), "quotaBytes", saved.getQuotaBytes()));
        return BillingDtos.BillingPlanResponse.from(saved);
    }

    @Transactional
    public BillingDtos.BillingPlanResponse updatePlan(String id, BillingDtos.BillingPlanRequest request) {
        BillingPlan plan = plans.findById(id).orElseThrow(() -> new IllegalArgumentException("Billing plan not found"));
        apply(plan, request);
        BillingPlan saved = plans.save(plan);
        auditLogService.record("BILLING_PLAN_UPDATED", "BILLING_PLAN", saved.getId(), saved.getName(),
                "Updated quota plan " + saved.getName());
        return BillingDtos.BillingPlanResponse.from(saved);
    }

    @Transactional
    public BillingDtos.CheckoutResponse checkout(String planId) {
        User user = currentUser();
        BillingPlan plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("Billing plan not found"));
        if (!plan.isActive()) throw new IllegalArgumentException("Billing plan is inactive");
        PayosConfig config = payosConfig();
        if (!config.enabled) throw new IllegalStateException("PayOS is disabled");

        BillingOrder order = new BillingOrder();
        order.setUserId(user.getId());
        order.setPlanId(plan.getId());
        order.setPlanName(plan.getName());
        order.setQuotaBytes(plan.getQuotaBytes());
        order.setAmount(plan.getPrice());
        order.setCurrency(plan.getCurrency());
        order.setProviderOrderCode(System.currentTimeMillis());
        orders.save(order);

        Map<String, Object> body = new HashMap<>();
        body.put("orderCode", order.getProviderOrderCode());
        body.put("amount", order.getAmount());
        body.put("description", trim("HaoBox " + plan.getName(), 25));
        body.put("returnUrl", config.returnUrl);
        body.put("cancelUrl", config.cancelUrl);
        body.put("signature", signature(order.getAmount(), config.cancelUrl, trim("HaoBox " + plan.getName(), 25),
                order.getProviderOrderCode(), config.returnUrl, config.checksumKey));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", config.clientId);
        headers.set("x-api-key", config.apiKey);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity("https://api-merchant.payos.vn/v2/payment-requests",
                new HttpEntity<>(body, headers), JsonNode.class);
        JsonNode data = response.getBody() == null ? null : response.getBody().path("data");
        if (data == null || data.isMissingNode() || !hasText(data.path("checkoutUrl").asText())) {
            throw new IllegalStateException("Could not create PayOS checkout link");
        }
        order.setCheckoutUrl(data.path("checkoutUrl").asText());
        order.setPaymentLinkId(data.path("paymentLinkId").asText(""));
        orders.save(order);
        return new BillingDtos.CheckoutResponse(order.getId(), order.getProviderOrderCode(), order.getCheckoutUrl());
    }

    @Transactional
    public void handlePayosWebhook(JsonNode payload) {
        PayosConfig config = payosConfig();
        JsonNode data = payload.path("data");
        String signature = payload.path("signature").asText("");
        if (!verifyWebhook(data, signature, config.checksumKey)) {
            throw new IllegalArgumentException("Invalid PayOS webhook signature");
        }
        long orderCode = data.path("orderCode").asLong();
        String code = payload.path("code").asText("");
        String status = data.path("status").asText("");
        if (!"00".equals(code) && !"PAID".equalsIgnoreCase(status)) {
            return;
        }
        BillingOrder order = orders.findByProviderOrderCode(orderCode).orElse(null);
        if (order == null) {
            return;
        }
        if ("PAID".equals(order.getStatus())) return;
        order.setStatus("PAID");
        order.setPaidAt(Instant.now());
        orders.save(order);
        activateSubscription(order);
    }

    @Transactional(readOnly = true)
    public Page<BillingDtos.BillingOrderResponse> myOrders(int page, int size) {
        User user = currentUser();
        return orders.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)))
                .map(BillingDtos.BillingOrderResponse::from);
    }

    private void activateSubscription(BillingOrder order) {
        User user = users.findById(order.getUserId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        subscriptions.findFirstByUserIdAndActiveTrueOrderByStartsAtDesc(user.getId()).ifPresent(subscription -> {
            subscription.setActive(false);
            subscriptions.save(subscription);
        });
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(user.getId());
        subscription.setPlanId(order.getPlanId());
        subscription.setPlanName(order.getPlanName());
        subscription.setQuotaBytes(order.getQuotaBytes());
        BillingPlan plan = plans.findById(order.getPlanId()).orElse(null);
        if (plan != null && plan.getDurationDays() > 0) {
            subscription.setExpiresAt(Instant.now().plusSeconds(plan.getDurationDays() * 86400L));
        }
        subscriptions.save(subscription);
        user.setStorageQuotaBytes(order.getQuotaBytes());
        users.save(user);
        notificationService.notifyUser(user.getId(), "BILLING_UPGRADED", "Storage upgraded",
                "Your HaoBox storage quota was upgraded to " + order.getPlanName() + ".", "BILLING_ORDER", order.getId());
        auditLogService.record("BILLING_ORDER_PAID", "BILLING_ORDER", order.getId(), order.getPlanName(),
                "Paid quota plan " + order.getPlanName(), Map.of("userId", user.getId(), "amount", order.getAmount()));
    }

    private void apply(BillingPlan plan, BillingDtos.BillingPlanRequest request) {
        plan.setName(clean(request.getName()));
        plan.setQuotaBytes(request.getQuotaGb() * GB);
        plan.setPrice(request.getPrice());
        plan.setCurrency(hasText(request.getCurrency()) ? request.getCurrency().trim().toUpperCase() : "VND");
        plan.setDurationDays(request.getDurationDays());
        plan.setDescription(clean(request.getDescription()));
        plan.setActive(request.isActive());
        plan.setSortOrder(request.getSortOrder());
        plan.setUpdatedAt(Instant.now());
    }

    private PayosConfig payosConfig() {
        return new PayosConfig(bool(ENABLED, false), value(CLIENT_ID, ""), secret(API_KEY), secret(CHECKSUM_KEY),
                value(RETURN_URL, webBaseUrl + "/billing/success"), value(CANCEL_URL, webBaseUrl + "/billing/cancel"));
    }

    private boolean verifyWebhook(JsonNode data, String provided, String checksumKey) {
        if (!hasText(provided) || data == null || data.isMissingNode()) return false;
        try {
            Map<String, Object> map = objectMapper.convertValue(data, Map.class);
            String canonical = map.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + valueString(entry.getValue()))
                    .collect(Collectors.joining("&"));
            return hmac(canonical, checksumKey).equalsIgnoreCase(provided);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String signature(long amount, String cancelUrl, String description, long orderCode, String returnUrl, String checksumKey) {
        String data = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
        return hmac(data, checksumKey);
    }

    private String hmac(String data, String key) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign PayOS request", exception);
        }
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new IllegalStateException("Authentication is required");
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private void save(String key, String value) {
        AppSetting setting = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value == null ? "" : value);
        setting.setUpdatedAt(Instant.now());
        settings.save(setting);
    }

    private String value(String key, String fallback) {
        return settings.findById(key).map(AppSetting::getValue).orElse(fallback);
    }

    private String secret(String key) {
        return encryptionService.decrypt(value(key, ""));
    }

    private boolean bool(String key, boolean fallback) {
        return Boolean.parseBoolean(value(key, String.valueOf(fallback)));
    }

    private String valueString(Object value) {
        if (value instanceof List || value instanceof Map) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception ignored) {
            }
        }
        return String.valueOf(value);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String trim(String value, int max) {
        String clean = clean(value);
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class PayosConfig {
        private final boolean enabled;
        private final String clientId;
        private final String apiKey;
        private final String checksumKey;
        private final String returnUrl;
        private final String cancelUrl;

        private PayosConfig(boolean enabled, String clientId, String apiKey, String checksumKey, String returnUrl, String cancelUrl) {
            this.enabled = enabled;
            this.clientId = clientId;
            this.apiKey = apiKey;
            this.checksumKey = checksumKey;
            this.returnUrl = returnUrl;
            this.cancelUrl = cancelUrl;
        }
    }
}
