package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.EmailTemplateRequest;
import cloud.haovo.filemanager.api.EmailTemplateResponse;
import cloud.haovo.filemanager.domain.AppSetting;
import cloud.haovo.filemanager.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmailTemplateService {
    public static final String TWO_FACTOR = "TWO_FACTOR_OTP";
    public static final String FORGOT_PASSWORD = "FORGOT_PASSWORD";
    public static final String VERIFY_EMAIL = "VERIFY_EMAIL";
    public static final String FILE_SHARED = "FILE_SHARED";
    public static final String ADMIN_ALERT = "ADMIN_ALERT";
    public static final String USER_INVITE = "USER_INVITE";
    public static final String ADMIN_PASSWORD_RESET = "ADMIN_PASSWORD_RESET";

    private final AppSettingRepository settings;
    private final AuditLogService auditLogService;
    private final String webBaseUrl;

    public EmailTemplateService(AppSettingRepository settings, AuditLogService auditLogService,
            @Value("${app.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.settings = settings;
        this.auditLogService = auditLogService;
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public List<EmailTemplateResponse> list() {
        return defaults().keySet().stream().map(this::get).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmailTemplateResponse get(String key) {
        TemplateDefault fallback = requireDefault(key);
        return new EmailTemplateResponse(key, fallback.name,
                value(key, "subject", fallback.subject),
                value(key, "html", fallback.htmlBody),
                value(key, "text", fallback.textBody),
                fallback.variables);
    }

    @Transactional
    public EmailTemplateResponse update(String key, EmailTemplateRequest request) {
        requireDefault(key);
        save(key, "subject", request.getSubject());
        save(key, "html", request.getHtmlBody());
        save(key, "text", request.getTextBody());
        auditLogService.record("EMAIL_TEMPLATE_UPDATED", "SETTING", key, key,
                "Updated email template " + key);
        return get(key);
    }

    @Transactional
    public EmailTemplateResponse reset(String key) {
        TemplateDefault fallback = requireDefault(key);
        save(key, "subject", fallback.subject);
        save(key, "html", fallback.htmlBody);
        save(key, "text", fallback.textBody);
        auditLogService.record("EMAIL_TEMPLATE_RESET", "SETTING", key, key,
                "Reset email template " + key);
        return get(key);
    }

    @Transactional(readOnly = true)
    public RenderedEmail render(String key, Map<String, String> variables) {
        EmailTemplateResponse template = get(key);
        Map<String, String> mergedVariables = new LinkedHashMap<>();
        mergedVariables.put("logoUrl", webBaseUrl + "/logo.png");
        if (variables != null) {
            mergedVariables.putAll(variables);
        }
        return new RenderedEmail(
                renderText(template.getSubject(), mergedVariables, false),
                renderText(template.getHtmlBody(), mergedVariables, true),
                renderText(template.getTextBody(), mergedVariables, false));
    }

    private String value(String key, String field, String fallback) {
        return settings.findById(settingKey(key, field)).map(AppSetting::getValue).orElse(fallback);
    }

    private void save(String key, String field, String value) {
        String id = settingKey(key, field);
        AppSetting setting = settings.findById(id).orElseGet(() -> new AppSetting(id, value));
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now());
        settings.save(setting);
    }

    private String settingKey(String key, String field) {
        return "emailTemplate." + key + "." + field;
    }

    private TemplateDefault requireDefault(String key) {
        TemplateDefault template = defaults().get(key);
        if (template == null) {
            throw new IllegalArgumentException("Email template not found");
        }
        return template;
    }

    private String renderText(String template, Map<String, String> variables, boolean html) {
        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}",
                    html ? escapeHtml(entry.getValue()) : safe(entry.getValue()));
        }
        return rendered;
    }

    private String escapeHtml(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, TemplateDefault> defaults() {
        Map<String, TemplateDefault> map = new LinkedHashMap<>();
        map.put(TWO_FACTOR, new TemplateDefault("2FA OTP", "Your HaoBox verification code",
                twoFactorHtml(),
                "HaoBox\n{{logoUrl}}\n\nHi {{fullName}},\n\nYour verification code is:\n{{code}}\n\nThis code expires in {{expiresInMinutes}} minutes.\n\nIf you did not try to sign in, please change your password or contact an administrator.\n\nSent to {{email}} by HaoBox.",
                vars("logoUrl", "fullName", "email", "code", "expiresInMinutes")));
        map.put(FORGOT_PASSWORD, new TemplateDefault("Forgot Password", "Reset your HaoBox password",
                forgotPasswordHtml(),
                "HaoBox\n{{logoUrl}}\n\nHi {{fullName}},\n\nYou have submitted a password change request.\n\nConfirm the password change here:\n{{resetLink}}\n\nThis link expires in {{expiresInMinutes}} minutes.\n\nIf you did not request this, you can safely ignore this email.",
                vars("logoUrl", "fullName", "email", "resetLink", "expiresInMinutes")));
        map.put(VERIFY_EMAIL, new TemplateDefault("Verify Email", "Verify your HaoBox email",
                verifyEmailHtml(),
                "HaoBox\n{{logoUrl}}\n\nHi {{fullName}},\n\nVerify your email to activate your HaoBox account:\n{{verificationLink}}\n\nThis link expires in {{expiresInMinutes}} minutes.\n\nSent to {{email}} by HaoBox.",
                vars("logoUrl", "fullName", "email", "verificationLink", "expiresInMinutes")));
        map.put(FILE_SHARED, new TemplateDefault("File Shared", "{{ownerName}} shared a file with you",
                fileSharedHtml(),
                "HaoBox\n{{logoUrl}}\n\nHi {{fullName}},\n\n{{ownerName}} shared a file with you.\nFile: {{fileName}}\nFrom: {{ownerEmail}}\n\nOpen file: {{fileLink}}",
                vars("logoUrl", "fullName", "email", "ownerName", "ownerEmail", "fileName", "fileLink")));
        map.put(ADMIN_ALERT, new TemplateDefault("Admin Alert", "HaoBox alert: {{title}}",
                adminAlertHtml(),
                "HaoBox\n{{logoUrl}}\n\n{{title}}\n\n{{message}}\n\nTime: {{time}}",
                vars("logoUrl", "title", "message", "time")));
        map.put(USER_INVITE, new TemplateDefault("User Invite", "You have been invited to HaoBox",
                userInviteHtml(),
                "HaoBox\n{{logoUrl}}\n\nHi {{fullName}},\n\nAn account has been created for you.\nEmail: {{email}}\nTemporary password: {{password}}\n\nSign in: {{loginLink}}",
                vars("logoUrl", "fullName", "email", "password", "loginLink")));
        map.put(ADMIN_PASSWORD_RESET, new TemplateDefault("Admin Password Reset", "Your HaoBox password was reset",
                adminPasswordResetHtml(),
                "HaoBox\n{{logoUrl}}\n\nHi {{fullName}},\n\nAn administrator reset your password.\nTemporary password: {{password}}\n\nSign in: {{loginLink}}\n\nPlease change this password after signing in.",
                vars("logoUrl", "fullName", "email", "password", "loginLink")));
        return map;
    }

    private String twoFactorHtml() {
        return emailFrame(
                "Your verification code",
                "Use this code to finish signing in to HaoBox.",
                String.join("", Arrays.asList(
                        "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.7;color:#10241b;\">Hi {{fullName}},</p>",
                        "<p style=\"margin:0 0 22px;font-size:15px;line-height:1.7;color:#10241b;\">Enter this code in HaoBox to complete your two-factor authentication.</p>",
                        "<div style=\"margin:24px 0;text-align:center;\">",
                        "<div style=\"display:inline-block;background:#e8f4ed;border:1px solid #bfe5cf;border-radius:12px;padding:18px 26px;\">",
                        "<div style=\"font-size:34px;line-height:1;font-weight:800;letter-spacing:8px;color:#12784f;\">{{code}}</div>",
                        "</div>",
                        "</div>",
                        "<div style=\"margin:22px 0;padding:14px 16px;background:#f6f8f5;border:1px solid #dfe8e1;border-radius:10px;\">",
                        "<p style=\"margin:0;font-size:14px;line-height:1.7;color:#66756d;\">This code expires in <strong style=\"color:#10241b;\">{{expiresInMinutes}} minutes</strong>.</p>",
                        "</div>",
                        "<p style=\"margin:0;font-size:14px;line-height:1.7;color:#66756d;\">If you did not try to sign in, please change your password or contact an administrator.</p>")),
                "Sent to {{email}} by HaoBox.");
    }

    private String forgotPasswordHtml() {
        return emailFrame(
                "Password change request",
                "Confirm this request to update your HaoBox password.",
                String.join("", Arrays.asList(
                        "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.7;color:#10241b;\">Hi {{fullName}},</p>",
                        "<p style=\"margin:0 0 22px;font-size:15px;line-height:1.7;color:#10241b;\">If this was you, confirm the password change using the button below.</p>",
                        "<div style=\"margin:22px 0;padding:14px 16px;background:#f6f8f5;border:1px solid #dfe8e1;border-radius:10px;\">",
                        "<p style=\"margin:0 0 8px;font-size:11px;letter-spacing:.12em;text-transform:uppercase;font-weight:700;color:#66756d;\">Secure link</p>",
                        "<a href=\"{{resetLink}}\" style=\"word-break:break-all;font-size:13px;color:#12784f;text-decoration:none;\">{{resetLink}}</a>",
                        "</div>",
                        "<div style=\"height:1px;background:#dfe8e1;margin:26px 0;\"></div>",
                        "<div style=\"text-align:center;margin:28px 0;\">",
                        "<a href=\"{{resetLink}}\" style=\"display:inline-block;background:#12784f;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;padding:13px 26px;border-radius:8px;\">Confirm</a>",
                        "</div>",
                        "<p style=\"margin:0 0 8px;font-size:14px;line-height:1.7;color:#66756d;\">This link expires in <strong style=\"color:#10241b;\">{{expiresInMinutes}} minutes</strong>.</p>",
                        "<p style=\"margin:0;font-size:14px;line-height:1.7;color:#66756d;\">If you did not request this, you can safely ignore this email.</p>")),
                "HaoBox helps you store, find, and share your files securely.");
    }

    private String verifyEmailHtml() {
        return emailFrame(
                "Verify your email",
                "Activate your HaoBox account with one secure confirmation.",
                String.join("", Arrays.asList(
                        "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.7;color:#10241b;\">Hi {{fullName}},</p>",
                        "<p style=\"margin:0 0 22px;font-size:15px;line-height:1.7;color:#10241b;\">Confirm this email address to finish setting up your HaoBox account.</p>",
                        "<div style=\"text-align:center;margin:28px 0;\">",
                        "<a href=\"{{verificationLink}}\" style=\"display:inline-block;background:#12784f;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;padding:13px 26px;border-radius:8px;\">Verify email</a>",
                        "</div>",
                        "<div style=\"margin:22px 0;padding:14px 16px;background:#f6f8f5;border:1px solid #dfe8e1;border-radius:10px;\">",
                        "<p style=\"margin:0 0 8px;font-size:11px;letter-spacing:.12em;text-transform:uppercase;font-weight:700;color:#66756d;\">Verification link</p>",
                        "<a href=\"{{verificationLink}}\" style=\"word-break:break-all;font-size:13px;color:#12784f;text-decoration:none;\">{{verificationLink}}</a>",
                        "</div>",
                        "<p style=\"margin:0;font-size:14px;line-height:1.7;color:#66756d;\">This link expires in <strong style=\"color:#10241b;\">{{expiresInMinutes}} minutes</strong>.</p>")),
                "Sent to {{email}} by HaoBox.");
    }

    private String fileSharedHtml() {
        return emailFrame(
                "A file was shared with you",
                "{{ownerName}} added you to a HaoBox file.",
                String.join("", Arrays.asList(
                        "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.7;color:#10241b;\">Hi {{fullName}},</p>",
                        "<p style=\"margin:0 0 20px;font-size:15px;line-height:1.7;color:#10241b;\">{{ownerName}} shared a file with your HaoBox account.</p>",
                        "<div style=\"margin:22px 0;padding:16px;background:#f6f8f5;border:1px solid #dfe8e1;border-radius:10px;\">",
                        "<p style=\"margin:0 0 8px;font-size:11px;letter-spacing:.12em;text-transform:uppercase;font-weight:700;color:#66756d;\">Shared file</p>",
                        "<p style=\"margin:0;font-size:16px;font-weight:800;color:#10241b;word-break:break-word;\">{{fileName}}</p>",
                        "<p style=\"margin:8px 0 0;font-size:13px;color:#66756d;\">Shared by {{ownerEmail}}</p>",
                        "</div>",
                        "<div style=\"text-align:center;margin:28px 0;\">",
                        "<a href=\"{{fileLink}}\" style=\"display:inline-block;background:#12784f;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;padding:13px 26px;border-radius:8px;\">Open file</a>",
                        "</div>")),
                "You are receiving this because {{ownerName}} shared a file with {{email}}.");
    }

    private String adminAlertHtml() {
        return emailFrame(
                "System alert",
                "A HaoBox event needs administrator attention.",
                String.join("", Arrays.asList(
                        "<div style=\"margin:0 0 22px;padding:16px;background:#fff8e6;border:1px solid #f2d98d;border-radius:10px;\">",
                        "<p style=\"margin:0 0 8px;font-size:11px;letter-spacing:.12em;text-transform:uppercase;font-weight:700;color:#9a6500;\">Alert</p>",
                        "<p style=\"margin:0;font-size:18px;font-weight:800;color:#10241b;\">{{title}}</p>",
                        "</div>",
                        "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.7;color:#10241b;\">{{message}}</p>",
                        "<div style=\"margin:22px 0 0;padding:14px 16px;background:#f6f8f5;border:1px solid #dfe8e1;border-radius:10px;\">",
                        "<p style=\"margin:0;font-size:13px;color:#66756d;\">Time: <strong style=\"color:#10241b;\">{{time}}</strong></p>",
                        "</div>")),
                "This alert was generated automatically by HaoBox.");
    }

    private String userInviteHtml() {
        return emailFrame(
                "You have been invited",
                "Your HaoBox workspace account is ready.",
                String.join("", Arrays.asList(
                        "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.7;color:#10241b;\">Hi {{fullName}},</p>",
                        "<p style=\"margin:0 0 22px;font-size:15px;line-height:1.7;color:#10241b;\">An administrator created a HaoBox account for you.</p>",
                        "<div style=\"margin:22px 0;padding:16px;background:#f6f8f5;border:1px solid #dfe8e1;border-radius:10px;\">",
                        "<p style=\"margin:0 0 10px;font-size:11px;letter-spacing:.12em;text-transform:uppercase;font-weight:700;color:#66756d;\">Sign-in details</p>",
                        "<p style=\"margin:0 0 8px;font-size:14px;color:#10241b;\">Email: <strong>{{email}}</strong></p>",
                        "<p style=\"margin:0;font-size:14px;color:#10241b;\">Temporary password: <strong>{{password}}</strong></p>",
                        "</div>",
                        "<div style=\"text-align:center;margin:28px 0;\">",
                        "<a href=\"{{loginLink}}\" style=\"display:inline-block;background:#12784f;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;padding:13px 26px;border-radius:8px;\">Sign in</a>",
                        "</div>")),
                "Please change your temporary password after signing in.");
    }

    private String adminPasswordResetHtml() {
        return emailFrame(
                "Your password was reset",
                "An administrator updated your HaoBox password.",
                String.join("", Arrays.asList(
                        "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.7;color:#10241b;\">Hi {{fullName}},</p>",
                        "<p style=\"margin:0 0 22px;font-size:15px;line-height:1.7;color:#10241b;\">An administrator reset your password. Use the temporary password below to sign in.</p>",
                        "<div style=\"margin:22px 0;padding:16px;background:#f6f8f5;border:1px solid #dfe8e1;border-radius:10px;\">",
                        "<p style=\"margin:0 0 8px;font-size:11px;letter-spacing:.12em;text-transform:uppercase;font-weight:700;color:#66756d;\">Temporary password</p>",
                        "<p style=\"margin:0;font-size:22px;font-weight:800;color:#12784f;word-break:break-all;\">{{password}}</p>",
                        "</div>",
                        "<div style=\"text-align:center;margin:28px 0;\">",
                        "<a href=\"{{loginLink}}\" style=\"display:inline-block;background:#12784f;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;padding:13px 26px;border-radius:8px;\">Sign in</a>",
                        "</div>",
                        "<p style=\"margin:0;font-size:14px;line-height:1.7;color:#66756d;\">Please change this password after signing in.</p>")),
                "If you did not expect this change, contact an administrator.");
    }

    private String emailFrame(String title, String subtitle, String body, String footer) {
        return String.join("", Arrays.asList(
                "<div style=\"margin:0;padding:0;background:#f6f8f5;font-family:Arial,Helvetica,sans-serif;color:#10241b;\">",
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;background:#f6f8f5;\">",
                "<tr><td align=\"center\" style=\"padding:32px 16px;\">",
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:560px;border-collapse:collapse;background:#ffffff;border:1px solid #dfe8e1;border-radius:14px;overflow:hidden;\">",
                "<tr><td align=\"center\" style=\"background:#10241b;padding:34px 28px;color:#ffffff;\">",
                "<img src=\"{{logoUrl}}\" alt=\"HaoBox\" style=\"display:block;height:42px;max-width:190px;margin:0 auto 22px;\" />",
                "<h1 style=\"margin:0;font-size:26px;line-height:1.35;font-weight:800;\">", title, "</h1>",
                "<p style=\"margin:10px 0 0;font-size:14px;line-height:1.7;color:#c9d8cf;\">", subtitle, "</p>",
                "</td></tr>",
                "<tr><td style=\"padding:30px 28px;\">", body, "</td></tr>",
                "<tr><td style=\"padding:18px 28px;background:#f6f8f5;border-top:1px solid #dfe8e1;\">",
                "<p style=\"margin:0;font-size:12px;line-height:1.6;color:#66756d;\">", footer, "</p>",
                "</td></tr>",
                "</table>",
                "</td></tr>",
                "</table>",
                "</div>"));
    }

    private String shell(String body) {
        return "<div style=\"font-family:Arial,sans-serif;color:#12241b;line-height:1.6;max-width:560px;margin:0 auto;padding:24px;\">"
                + "<img src=\"{{logoUrl}}\" alt=\"HaoBox\" style=\"display:block;height:34px;max-width:180px;margin:0 0 24px;\" />"
                + body
                + "<p style=\"margin-top:28px;color:#66756d;font-size:12px;\">If you did not request this, you can ignore this email.</p></div>";
    }

    private List<String> vars(String... variables) {
        return Arrays.asList(variables);
    }

    private static class TemplateDefault {
        private final String name;
        private final String subject;
        private final String htmlBody;
        private final String textBody;
        private final List<String> variables;

        private TemplateDefault(String name, String subject, String htmlBody, String textBody, List<String> variables) {
            this.name = name;
            this.subject = subject;
            this.htmlBody = htmlBody;
            this.textBody = textBody;
            this.variables = variables;
        }
    }

    public static class RenderedEmail {
        private final String subject;
        private final String htmlBody;
        private final String textBody;

        public RenderedEmail(String subject, String htmlBody, String textBody) {
            this.subject = subject;
            this.htmlBody = htmlBody;
            this.textBody = textBody;
        }

        public String getSubject() {
            return subject;
        }

        public String getHtmlBody() {
            return htmlBody;
        }

        public String getTextBody() {
            return textBody;
        }
    }
}
