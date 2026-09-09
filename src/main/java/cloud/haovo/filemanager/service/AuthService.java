package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.AuthRequests;
import cloud.haovo.filemanager.api.AuthResponse;
import cloud.haovo.filemanager.domain.EmailToken;
import cloud.haovo.filemanager.domain.TwoFactorChallenge;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.repository.EmailTokenRepository;
import cloud.haovo.filemanager.repository.TwoFactorChallengeRepository;
import cloud.haovo.filemanager.repository.UserRepository;
import cloud.haovo.filemanager.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TWO_FACTOR_TTL_SECONDS = 300;
    private static final long EMAIL_VERIFICATION_TTL_SECONDS = 24 * 60 * 60;
    private static final long PASSWORD_RESET_TTL_SECONDS = 30 * 60;

    private final UserRepository users;
    private final EmailTokenRepository emailTokens;
    private final TwoFactorChallengeRepository twoFactorChallenges;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserQuotaService userQuotaService;
    private final AuditLogService auditLogService;
    private final SmtpSettingsService smtpSettingsService;
    private final EmailTemplateService emailTemplateService;
    private final String webBaseUrl;

    public AuthService(UserRepository users, EmailTokenRepository emailTokens,
            TwoFactorChallengeRepository twoFactorChallenges,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService,
            RefreshTokenService refreshTokenService, UserQuotaService userQuotaService,
            AuditLogService auditLogService, SmtpSettingsService smtpSettingsService,
            EmailTemplateService emailTemplateService,
            @Value("${app.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.users = users;
        this.emailTokens = emailTokens;
        this.twoFactorChallenges = twoFactorChallenges;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userQuotaService = userQuotaService;
        this.auditLogService = auditLogService;
        this.smtpSettingsService = smtpSettingsService;
        this.emailTemplateService = emailTemplateService;
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    public AuthResponse register(AuthRequests.Register request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email))
            throw new IllegalArgumentException("Email is already registered");
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.getFullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);
        user = users.save(user);
        sendEmailVerification(user);
        return AuthResponse.emailVerificationRequired(user.getEmail());
    }

    public AuthResponse login(AuthRequests.Login request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        User user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isEmailVerified()) {
            sendEmailVerification(user);
            return AuthResponse.emailVerificationRequired(user.getEmail());
        }
        if (user.isTwoFactorEnabled()) {
            return createTwoFactorChallenge(user);
        }
        return session(user);
    }

    @Transactional
    public void verifyEmail(AuthRequests.VerifyEmail request) {
        EmailToken token = consumeEmailToken(request.getToken(), EmailTemplateService.VERIFY_EMAIL);
        User user = token.getUser();
        user.setEmailVerified(true);
        users.save(user);
        auditLogService.system("EMAIL_VERIFIED", "USER", user.getId(), user.getEmail(),
                "Verified email " + user.getEmail(), Map.of("email", user.getEmail()));
    }

    @Transactional
    public void resendVerification(AuthRequests.ResendVerification request) {
        users.findByEmailIgnoreCase(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendEmailVerification);
    }

    @Transactional
    public void forgotPassword(AuthRequests.ForgotPassword request) {
        users.findByEmailIgnoreCase(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .ifPresent(this::sendPasswordReset);
    }

    @Transactional
    public void resetPassword(AuthRequests.ResetPassword request) {
        EmailToken token = consumeEmailToken(request.getToken(), EmailTemplateService.FORGOT_PASSWORD);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        users.save(user);
        refreshTokenService.revokeAll(user);
        auditLogService.system("PASSWORD_RESET_COMPLETED", "USER", user.getId(), user.getEmail(),
                "Completed password reset for " + user.getEmail(), Map.of("email", user.getEmail()));
    }

    @Transactional
    public AuthResponse verifyTwoFactor(AuthRequests.VerifyTwoFactor request) {
        TwoFactorChallenge challenge = twoFactorChallenges.findByIdAndConsumedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> new IllegalArgumentException("Verification code is invalid or expired"));
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            challenge.setConsumedAt(Instant.now());
            twoFactorChallenges.save(challenge);
            throw new IllegalArgumentException("Verification code expired. Please sign in again.");
        }
        if (!passwordEncoder.matches(request.getCode(), challenge.getCodeHash())) {
            throw new IllegalArgumentException("Verification code is incorrect");
        }
        challenge.setConsumedAt(Instant.now());
        twoFactorChallenges.save(challenge);
        User user = challenge.getUser();
        if (!user.isEnabled()) throw new IllegalArgumentException("User is disabled");
        auditLogService.record("TWO_FACTOR_VERIFIED", "USER", user.getId(), user.getEmail(),
                "Completed two-factor authentication");
        return session(user);
    }

    public AuthResponse refresh(AuthRequests.Refresh request) {
        User user = refreshTokenService.consume(request.getRefreshToken());
        if (!user.isEnabled()) throw new IllegalArgumentException("User is disabled");
        return session(user);
    }

    public void logout(AuthRequests.Refresh request) {
        refreshTokenService.revoke(request.getRefreshToken());
    }

    public AuthResponse.UserResponse updateProfile(String email, AuthRequests.UpdateProfile request) {
        User user = findByEmail(email);
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
        if (fullName.isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        user.setFullName(fullName);
        user = users.save(user);
        return AuthResponse.UserResponse.from(user, userQuotaService.effectiveQuotaBytes(user),
                userQuotaService.usedBytes(user));
    }

    @Transactional
    public AuthResponse changePassword(String email, AuthRequests.ChangePassword request) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user = users.save(user);
        refreshTokenService.revokeAll(user);
        return session(user);
    }

    @Transactional
    public AuthResponse.UserResponse updateTwoFactor(String email, AuthRequests.UpdateTwoFactor request) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setTwoFactorEnabled(request.isEnabled());
        user = users.save(user);
        auditLogService.record(request.isEnabled() ? "TWO_FACTOR_ENABLED" : "TWO_FACTOR_DISABLED",
                "USER", user.getId(), user.getEmail(),
                request.isEnabled() ? "Enabled two-factor authentication" : "Disabled two-factor authentication",
                Map.of("enabled", request.isEnabled()));
        return AuthResponse.UserResponse.from(user, userQuotaService.effectiveQuotaBytes(user),
                userQuotaService.usedBytes(user));
    }

    public User findByEmail(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private AuthResponse session(User user) {
        return AuthResponse.session(jwtService.generateToken(user), refreshTokenService.create(user),
                AuthResponse.UserResponse.from(user, userQuotaService.effectiveQuotaBytes(user),
                        userQuotaService.usedBytes(user)));
    }

    private AuthResponse createTwoFactorChallenge(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        TwoFactorChallenge challenge = new TwoFactorChallenge();
        challenge.setUser(user);
        challenge.setCodeHash(passwordEncoder.encode(code));
        challenge.setExpiresAt(Instant.now().plusSeconds(TWO_FACTOR_TTL_SECONDS));
        challenge = twoFactorChallenges.save(challenge);
        EmailTemplateService.RenderedEmail email = emailTemplateService.render(EmailTemplateService.TWO_FACTOR,
                Map.of("fullName", user.getFullName(), "email", user.getEmail(), "code", code,
                        "expiresInMinutes", "5"));
        boolean sent = smtpSettingsService.sendTwoFactorCode(user, email);
        if (!sent) {
            log.info("Two-factor authentication code for {}: {}", user.getEmail(), code);
        }
        auditLogService.record("TWO_FACTOR_CHALLENGE_CREATED", "USER", user.getId(), user.getEmail(),
                "Created two-factor authentication challenge");
        return AuthResponse.twoFactorRequired(challenge.getId());
    }

    private void sendEmailVerification(User user) {
        String rawToken = createEmailToken(user, EmailTemplateService.VERIFY_EMAIL, EMAIL_VERIFICATION_TTL_SECONDS);
        String link = webBaseUrl + "/verify-email?token=" + rawToken;
        EmailTemplateService.RenderedEmail email = emailTemplateService.render(EmailTemplateService.VERIFY_EMAIL,
                Map.of("fullName", user.getFullName(), "email", user.getEmail(), "verificationLink", link,
                        "expiresInMinutes", "1440"));
        if (!smtpSettingsService.queueEmail(user.getEmail(), email.getSubject(), email.getHtmlBody(), email.getTextBody())) {
            log.info("Email verification link for {}: {}", user.getEmail(), link);
        }
    }

    private void sendPasswordReset(User user) {
        String rawToken = createEmailToken(user, EmailTemplateService.FORGOT_PASSWORD, PASSWORD_RESET_TTL_SECONDS);
        String link = webBaseUrl + "/reset-password?token=" + rawToken;
        EmailTemplateService.RenderedEmail email = emailTemplateService.render(EmailTemplateService.FORGOT_PASSWORD,
                Map.of("fullName", user.getFullName(), "email", user.getEmail(), "resetLink", link,
                        "expiresInMinutes", "30"));
        if (!smtpSettingsService.queueEmail(user.getEmail(), email.getSubject(), email.getHtmlBody(), email.getTextBody())) {
            log.info("Password reset link for {}: {}", user.getEmail(), link);
        }
        auditLogService.system("PASSWORD_RESET_REQUESTED", "USER", user.getId(), user.getEmail(),
                "Password reset requested for " + user.getEmail(), Map.of("email", user.getEmail()));
    }

    private String createEmailToken(User user, String type, long ttlSeconds) {
        String rawToken = java.util.UUID.randomUUID().toString() + "-" + java.util.UUID.randomUUID();
        EmailToken token = new EmailToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(passwordEncoder.encode(rawToken));
        token.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        emailTokens.save(token);
        return token.getId() + "." + rawToken;
    }

    private EmailToken consumeEmailToken(String value, String type) {
        String[] parts = (value == null ? "" : value).split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("This link is invalid or expired.");
        }
        EmailToken token = emailTokens.findByIdAndTypeAndConsumedAtIsNull(parts[0], type)
                .orElseThrow(() -> new IllegalArgumentException("This link is invalid or expired."));
        if (token.getExpiresAt().isBefore(Instant.now()) || !passwordEncoder.matches(parts[1], token.getTokenHash())) {
            token.setConsumedAt(Instant.now());
            emailTokens.save(token);
            throw new IllegalArgumentException("This link is invalid or expired.");
        }
        token.setConsumedAt(Instant.now());
        return emailTokens.save(token);
    }
}
