package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.AdminUserRequests;
import cloud.haovo.filemanager.api.AdminUserResponse;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserRole;
import cloud.haovo.filemanager.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UserQuotaService userQuotaService;
    private final AuditLogService auditLogService;
    private final SmtpSettingsService smtpSettingsService;
    private final EmailTemplateService emailTemplateService;
    private final String webBaseUrl;

    public AdminUserService(UserRepository users, PasswordEncoder passwordEncoder,
            UserQuotaService userQuotaService, AuditLogService auditLogService,
            SmtpSettingsService smtpSettingsService, EmailTemplateService emailTemplateService,
            @org.springframework.beans.factory.annotation.Value("${app.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.userQuotaService = userQuotaService;
        this.auditLogService = auditLogService;
        this.smtpSettingsService = smtpSettingsService;
        this.emailTemplateService = emailTemplateService;
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    public List<AdminUserResponse> listUsers() {
        return users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AdminUserResponse createUser(AdminUserRequests.Create request) {
        String email = cleanEmail(request.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(cleanName(request.getFullName()));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(EnumSet.of(parseRole(request.getRole())));
        user.setEnabled(request.isEnabled());
        user.setEmailVerified(true);
        user.setStorageQuotaBytes(normalizeQuota(request.getStorageQuotaBytes()));
        User saved = users.save(user);
        auditLogService.record("USER_CREATED", "USER", saved.getId(), saved.getEmail(),
                "Created user " + saved.getEmail(), Map.of("role", parsePrimaryRole(saved).name()));
        sendUserPasswordEmail(saved, request.getPassword(), EmailTemplateService.USER_INVITE);
        return toResponse(saved);
    }

    public AdminUserResponse resetPassword(String id, AdminUserRequests.ResetPassword request) {
        User user = findUser(id);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        User saved = users.save(user);
        auditLogService.record("USER_PASSWORD_RESET_BY_ADMIN", "USER", saved.getId(), saved.getEmail(),
                "Reset password for user " + saved.getEmail());
        sendUserPasswordEmail(saved, request.getNewPassword(), EmailTemplateService.ADMIN_PASSWORD_RESET);
        return toResponse(saved);
    }

    public AdminUserResponse updateRole(String id, AdminUserRequests.UpdateRole request) {
        User user = findUser(id);
        UserRole role = parseRole(request.getRole());
        if (isCurrentUser(user) && role != UserRole.ADMIN) {
            throw new IllegalArgumentException("You cannot remove your own admin role");
        }
        user.setRoles(EnumSet.of(role));
        User saved = users.save(user);
        auditLogService.record("USER_ROLE_CHANGED", "USER", saved.getId(), saved.getEmail(),
                "Changed user role for " + saved.getEmail() + " to " + role.name(), Map.of("role", role.name()));
        return toResponse(saved);
    }

    public AdminUserResponse updateStatus(String id, AdminUserRequests.UpdateStatus request) {
        User user = findUser(id);
        if (isCurrentUser(user) && !request.isEnabled()) {
            throw new IllegalArgumentException("You cannot disable your own account");
        }
        user.setEnabled(request.isEnabled());
        User saved = users.save(user);
        auditLogService.record(saved.isEnabled() ? "USER_UNLOCKED" : "USER_LOCKED", "USER", saved.getId(), saved.getEmail(),
                (saved.isEnabled() ? "Unlocked user " : "Locked user ") + saved.getEmail());
        return toResponse(saved);
    }

    public AdminUserResponse updateQuota(String id, AdminUserRequests.UpdateQuota request) {
        User user = findUser(id);
        user.setStorageQuotaBytes(normalizeQuota(request.getStorageQuotaBytes()));
        User saved = users.save(user);
        auditLogService.record("USER_QUOTA_CHANGED", "USER", saved.getId(), saved.getEmail(),
                "Changed storage quota for " + saved.getEmail(),
                Map.of("storageQuotaBytes", saved.getStorageQuotaBytes() == null ? "default" : saved.getStorageQuotaBytes()));
        return toResponse(saved);
    }

    private User findUser(String id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private boolean isCurrentUser(User user) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return user.getEmail().equalsIgnoreCase(email);
    }

    private String cleanEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanName(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        return cleaned;
    }

    private UserRole parseRole(String role) {
        try {
            return UserRole.valueOf((role == null ? "" : role.trim()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Role must be ADMIN or MEMBER");
        }
    }

    private Long normalizeQuota(Long quotaBytes) {
        if (quotaBytes == null) return null;
        if (quotaBytes < 0) return UserQuotaService.UNLIMITED_QUOTA;
        return quotaBytes;
    }

    private UserRole parsePrimaryRole(User user) {
        return user.safeRoles().contains(UserRole.ADMIN) ? UserRole.ADMIN : UserRole.MEMBER;
    }

    private AdminUserResponse toResponse(User user) {
        return AdminUserResponse.from(user, userQuotaService.effectiveQuotaBytes(user),
                userQuotaService.usedBytes(user));
    }

    private void sendUserPasswordEmail(User user, String password, String templateKey) {
        EmailTemplateService.RenderedEmail email = emailTemplateService.render(templateKey,
                Map.of("fullName", user.getFullName(), "email", user.getEmail(), "password", password,
                        "loginLink", webBaseUrl + "/login"));
        try {
            smtpSettingsService.queueEmail(user.getEmail(), email.getSubject(), email.getHtmlBody(), email.getTextBody());
        } catch (RuntimeException exception) {
            log.warn("User password email could not be sent to {}", user.getEmail(), exception);
        }
    }
}
