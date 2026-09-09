package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.ApiUsageLogResponse;
import cloud.haovo.filemanager.api.DeveloperApiKeyRequest;
import cloud.haovo.filemanager.api.DeveloperApiKeyResponse;
import cloud.haovo.filemanager.api.DeveloperStatsResponse;
import cloud.haovo.filemanager.domain.ApiUsageLog;
import cloud.haovo.filemanager.domain.DeveloperApiKey;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.repository.ApiUsageLogRepository;
import cloud.haovo.filemanager.repository.DeveloperApiKeyRepository;
import cloud.haovo.filemanager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DeveloperApiService {
    public static final String REQUEST_API_KEY = "haobox.apiKey";
    public static final String REQUEST_API_USER = "haobox.apiUser";

    private static final Set<String> ALLOWED_SCOPES = Set.of(
            "files:read", "files:write", "files:delete",
            "folders:read", "folders:write",
            "shares:read", "shares:write");
    private static final List<String> DEFAULT_SCOPES = List.of("files:read", "files:write", "folders:read", "folders:write");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeveloperApiKeyRepository apiKeys;
    private final ApiUsageLogRepository usageLogs;
    private final UserRepository users;
    private final AuditLogService auditLogService;
    private final int rateLimitPerMinute;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public DeveloperApiService(DeveloperApiKeyRepository apiKeys, ApiUsageLogRepository usageLogs,
            UserRepository users, AuditLogService auditLogService,
            @Value("${app.developer.rate-limit-per-minute:120}") int rateLimitPerMinute) {
        this.apiKeys = apiKeys;
        this.usageLogs = usageLogs;
        this.users = users;
        this.auditLogService = auditLogService;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public List<DeveloperApiKeyResponse> listKeys() {
        User user = currentUser();
        return apiKeys.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(DeveloperApiKeyResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public DeveloperApiKeyResponse createKey(DeveloperApiKeyRequest request) {
        User user = currentUser();
        String token = generateToken();
        DeveloperApiKey key = new DeveloperApiKey();
        key.setOwnerId(user.getId());
        key.setName(cleanName(request.getName()));
        key.setPrefix(token.substring(0, Math.min(token.length(), 16)));
        key.setKeyHash(hash(token));
        key.setScopes(String.join(",", normalizeScopes(request.getScopes())));
        key.setExpiresAt(request.getExpiresAt());
        DeveloperApiKey saved = apiKeys.save(key);
        auditLogService.record("DEVELOPER_API_KEY_CREATED", "API_KEY", saved.getId(), saved.getName(),
                "Created developer API key " + saved.getName(), Map.of("prefix", saved.getPrefix()));
        return DeveloperApiKeyResponse.from(saved, token);
    }

    @Transactional
    public void revokeKey(String id) {
        User user = currentUser();
        DeveloperApiKey key = apiKeys.findById(id).orElseThrow(() -> new IllegalArgumentException("API key not found"));
        if (!key.getOwnerId().equals(user.getId())) {
            throw new AccessDeniedException("You cannot manage this API key");
        }
        key.setActive(false);
        key.setRevokedAt(Instant.now());
        apiKeys.save(key);
        auditLogService.record("DEVELOPER_API_KEY_REVOKED", "API_KEY", key.getId(), key.getName(),
                "Revoked developer API key " + key.getName());
    }

    public Page<ApiUsageLogResponse> usageLogs(int page, int size) {
        User user = currentUser();
        return usageLogs.findByOwnerIdOrderByCreatedAtDesc(user.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(ApiUsageLogResponse::from);
    }

    public DeveloperStatsResponse stats() {
        User user = currentUser();
        List<DeveloperApiKey> keys = apiKeys.findByOwnerIdOrderByCreatedAtDesc(user.getId());
        Instant today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<ApiUsageLog> logs = usageLogs.findByOwnerIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 1000)).getContent();
        long requestsToday = logs.stream().filter(log -> !log.getCreatedAt().isBefore(today)).count();
        long failedToday = logs.stream().filter(log -> !log.getCreatedAt().isBefore(today) && log.getStatus() >= 400).count();
        return new DeveloperStatsResponse(keys.size(), keys.stream().filter(DeveloperApiKey::isActive).count(),
                requestsToday, failedToday, rateLimitPerMinute);
    }

    @Transactional
    public AuthenticatedApiKey authenticate(String token) {
        DeveloperApiKey key = apiKeys.findByKeyHash(hash(token))
                .orElseThrow(() -> new AccessDeniedException("Invalid API key"));
        if (!key.isActive() || key.getRevokedAt() != null) {
            throw new AccessDeniedException("This API key has been revoked");
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("This API key has expired");
        }
        enforceRateLimit(key.getId());
        User user = users.findById(key.getOwnerId()).orElseThrow(() -> new AccessDeniedException("API key owner not found"));
        if (!user.isEnabled()) {
            throw new AccessDeniedException("This account has been locked");
        }
        key.setLastUsedAt(Instant.now());
        apiKeys.save(key);
        return new AuthenticatedApiKey(key, user);
    }

    public void requireScope(String scope) {
        DeveloperApiKey key = currentApiKey();
        if (!scopes(key).contains(scope)) {
            throw new AccessDeniedException("This API key does not have the required scope");
        }
    }

    @Transactional
    public void recordUsage(HttpServletRequest request, int status, long durationMs, String errorMessage) {
        Object keyValue = request.getAttribute(REQUEST_API_KEY);
        Object userValue = request.getAttribute(REQUEST_API_USER);
        if (!(keyValue instanceof DeveloperApiKey) || !(userValue instanceof User)) {
            return;
        }
        DeveloperApiKey key = (DeveloperApiKey) keyValue;
        User user = (User) userValue;
        ApiUsageLog log = new ApiUsageLog();
        log.setOwnerId(user.getId());
        log.setApiKeyId(key.getId());
        log.setApiKeyName(key.getName());
        log.setMethod(request.getMethod());
        log.setPath(request.getRequestURI());
        log.setStatus(status);
        log.setDurationMs(durationMs);
        log.setIpAddress(clientIp(request));
        log.setErrorMessage(trim(errorMessage, 500));
        usageLogs.save(log);
    }

    private DeveloperApiKey currentApiKey() {
        Object value = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()
                .getAttribute(REQUEST_API_KEY, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        if (value instanceof DeveloperApiKey) return (DeveloperApiKey) value;
        throw new AccessDeniedException("API key authentication is required");
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authentication is required");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private void enforceRateLimit(String keyId) {
        long minute = Instant.now().getEpochSecond() / 60;
        RateWindow window = rateWindows.compute(keyId, (ignored, current) -> {
            if (current == null || current.minute != minute) return new RateWindow(minute, 1);
            current.count++;
            return current;
        });
        if (window.count > rateLimitPerMinute) {
            throw new RateLimitExceededException();
        }
    }

    private List<String> normalizeScopes(List<String> scopes) {
        List<String> requested = scopes == null || scopes.isEmpty() ? DEFAULT_SCOPES : scopes;
        return requested.stream()
                .map(scope -> scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT))
                .filter(scope -> !scope.isEmpty())
                .peek(scope -> {
                    if (!ALLOWED_SCOPES.contains(scope)) {
                        throw new IllegalArgumentException("Unsupported API scope: " + scope);
                    }
                })
                .distinct()
                .collect(Collectors.toList());
    }

    private Set<String> scopes(DeveloperApiKey key) {
        return Set.copyOf(normalizeScopes(List.of(key.getScopes().split(","))));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "hb_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash API key", exception);
        }
    }

    private String cleanName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("API key name is required");
        return trim(trimmed, 120);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return trim(forwarded.split(",")[0].trim(), 80);
        }
        return trim(request.getRemoteAddr(), 80);
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static class AuthenticatedApiKey {
        private final DeveloperApiKey key;
        private final User user;

        public AuthenticatedApiKey(DeveloperApiKey key, User user) {
            this.key = key;
            this.user = user;
        }

        public DeveloperApiKey getKey() {
            return key;
        }

        public User getUser() {
            return user;
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
    }

    private static class RateWindow {
        private final long minute;
        private int count;

        private RateWindow(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
