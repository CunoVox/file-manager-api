package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.SmtpSettingsRequest;
import cloud.haovo.filemanager.api.SmtpSettingsResponse;
import cloud.haovo.filemanager.domain.AppSetting;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.repository.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Service
public class SmtpSettingsService {
    private static final Logger log = LoggerFactory.getLogger(SmtpSettingsService.class);

    private static final String PREFIX = "smtp.";
    private static final String ENABLED = PREFIX + "enabled";
    private static final String HOST = PREFIX + "host";
    private static final String PORT = PREFIX + "port";
    private static final String USERNAME = PREFIX + "username";
    private static final String PASSWORD = PREFIX + "password";
    private static final String START_TLS = PREFIX + "startTls";
    private static final String AUTH = PREFIX + "auth";
    private static final String FROM_EMAIL = PREFIX + "fromEmail";
    private static final String FROM_NAME = PREFIX + "fromName";
    private static final String OTP_SUBJECT = PREFIX + "otpSubject";

    private final AppSettingRepository settings;
    private final AuditLogService auditLogService;
    private final Executor emailTaskExecutor;

    public SmtpSettingsService(AppSettingRepository settings, AuditLogService auditLogService,
            @Qualifier("emailTaskExecutor") Executor emailTaskExecutor) {
        this.settings = settings;
        this.auditLogService = auditLogService;
        this.emailTaskExecutor = emailTaskExecutor;
    }

    @Transactional(readOnly = true)
    public SmtpSettingsResponse getSettings() {
        return response();
    }

    @Transactional
    public SmtpSettingsResponse updateSettings(SmtpSettingsRequest request) {
        if (request.isEnabled()) {
            if (!hasText(request.getHost())) {
                throw new IllegalArgumentException("SMTP host is required");
            }
            if (!hasText(request.getFromEmail())) {
                throw new IllegalArgumentException("From email is required");
            }
        }
        save(ENABLED, String.valueOf(request.isEnabled()));
        save(HOST, clean(request.getHost()));
        save(PORT, String.valueOf(request.getPort()));
        save(USERNAME, clean(request.getUsername()));
        if (hasText(request.getPassword())) {
            save(PASSWORD, request.getPassword());
        }
        save(START_TLS, String.valueOf(request.isStartTls()));
        save(AUTH, String.valueOf(request.isAuth()));
        save(FROM_EMAIL, clean(request.getFromEmail()));
        save(FROM_NAME, clean(request.getFromName()));
        save(OTP_SUBJECT, clean(request.getOtpSubject()));
        auditLogService.record("SMTP_SETTINGS_UPDATED", "SETTING", "smtp", "SMTP Settings",
                "Updated SMTP settings", Map.of("enabled", request.isEnabled(), "host", clean(request.getHost())));
        return response();
    }

    @Transactional(readOnly = true)
    public boolean sendTwoFactorCode(User user, EmailTemplateService.RenderedEmail email) {
        return queueEmail(user.getEmail(), email.getSubject(), email.getHtmlBody(), email.getTextBody());
    }

    @Transactional(readOnly = true)
    public boolean queueEmail(String to, String subject, String htmlBody, String textBody) {
        SmtpSettingsResponse smtp = response();
        if (!canSend(smtp)) {
            return false;
        }
        try {
            emailTaskExecutor.execute(() -> sendEmailSafely(to, subject, htmlBody, textBody));
            return true;
        } catch (RejectedExecutionException exception) {
            log.warn("Email queue is full, could not queue email for {}", to, exception);
            return false;
        }
    }

    private void sendEmailSafely(String to, String subject, String htmlBody, String textBody) {
        try {
            sendEmail(to, subject, htmlBody, textBody);
        } catch (RuntimeException exception) {
            log.warn("Async email delivery failed for {}", to, exception);
        }
    }

    @Transactional(readOnly = true)
    public boolean sendEmail(String to, String subject, String htmlBody, String textBody) {
        SmtpSettingsResponse smtp = response();
        if (!canSend(smtp)) {
            return false;
        }
        try {
            JavaMailSenderImpl sender = sender(smtp);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setFrom(from(smtp));
            helper.setSubject(subject);
            helper.setText(textBody == null ? "" : textBody, htmlBody == null ? "" : htmlBody);
            sender.send(message);
            return true;
        } catch (MessagingException | RuntimeException exception) {
            log.warn("Could not send email to {}", to, exception);
            throw new IllegalStateException("OTP_EMAIL_FAILED", exception);
        }
    }

    @Transactional(readOnly = true)
    public void sendTest(String recipientEmail) {
        SmtpSettingsResponse smtp = response();
        if (!smtp.isEnabled()) {
            throw new IllegalStateException("SMTP is disabled");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setFrom(from(smtp));
            message.setSubject("HaoBox SMTP test");
            message.setText("SMTP is configured correctly. HaoBox can send verification emails.");
            sender(smtp).send(message);
        } catch (RuntimeException exception) {
            log.warn("SMTP test failed for {}", recipientEmail, exception);
            throw new IllegalStateException("SMTP test failed", exception);
        }
    }

    private JavaMailSenderImpl sender(SmtpSettingsResponse smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        sender.setUsername(smtp.getUsername());
        sender.setPassword(value(PASSWORD, ""));
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(smtp.isAuth()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStartTls()));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private SmtpSettingsResponse response() {
        return new SmtpSettingsResponse(
                bool(ENABLED, false),
                value(HOST, ""),
                integer(PORT, 587),
                value(USERNAME, ""),
                hasText(value(PASSWORD, "")),
                bool(START_TLS, true),
                bool(AUTH, true),
                value(FROM_EMAIL, ""),
                value(FROM_NAME, "HaoBox"),
                value(OTP_SUBJECT, "Your HaoBox verification code"));
    }

    private boolean canSend(SmtpSettingsResponse smtp) {
        return smtp.isEnabled()
                && hasText(smtp.getHost())
                && hasText(smtp.getFromEmail())
                && (!smtp.isAuth() || smtp.isPasswordConfigured());
    }

    private String from(SmtpSettingsResponse smtp) {
        if (!hasText(smtp.getFromName())) {
            return smtp.getFromEmail();
        }
        return smtp.getFromName() + " <" + smtp.getFromEmail() + ">";
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

    private boolean bool(String key, boolean fallback) {
        String value = value(key, String.valueOf(fallback));
        return Boolean.parseBoolean(value);
    }

    private int integer(String key, int fallback) {
        try {
            return Integer.parseInt(value(key, String.valueOf(fallback)));
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
