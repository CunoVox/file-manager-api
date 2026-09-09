package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.TrashPolicyService;
import cloud.haovo.filemanager.service.SmtpSettingsService;
import cloud.haovo.filemanager.service.EmailTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SettingsController {
    private final TrashPolicyService trashPolicyService;
    private final SmtpSettingsService smtpSettingsService;
    private final EmailTemplateService emailTemplateService;

    public SettingsController(TrashPolicyService trashPolicyService, SmtpSettingsService smtpSettingsService,
            EmailTemplateService emailTemplateService) {
        this.trashPolicyService = trashPolicyService;
        this.smtpSettingsService = smtpSettingsService;
        this.emailTemplateService = emailTemplateService;
    }

    @GetMapping("/settings/trash-policy")
    public TrashPolicyResponse trashPolicy() {
        return trashPolicyService.getPolicy();
    }

    @GetMapping("/admin/settings/trash-policy")
    @PreAuthorize("hasRole('ADMIN')")
    public TrashPolicyResponse adminTrashPolicy() {
        return trashPolicyService.getPolicy();
    }

    @GetMapping("/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsResponse adminSettings() {
        return new SystemSettingsResponse(trashPolicyService.getPolicy(), smtpSettingsService.getSettings());
    }

    @PatchMapping("/admin/settings/trash-policy")
    @PreAuthorize("hasRole('ADMIN')")
    public TrashPolicyResponse updateTrashPolicy(@Valid @RequestBody TrashPolicyRequest request) {
        return trashPolicyService.updatePolicy(request);
    }

    @PatchMapping("/admin/settings/smtp")
    @PreAuthorize("hasRole('ADMIN')")
    public SmtpSettingsResponse updateSmtp(@Valid @RequestBody SmtpSettingsRequest request) {
        return smtpSettingsService.updateSettings(request);
    }

    @PostMapping("/admin/settings/smtp/test")
    @PreAuthorize("hasRole('ADMIN')")
    public void testSmtp(@Valid @RequestBody SmtpTestRequest request) {
        smtpSettingsService.sendTest(request.getRecipientEmail());
    }

    @GetMapping("/admin/settings/email-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EmailTemplateResponse> emailTemplates() {
        return emailTemplateService.list();
    }

    @PatchMapping("/admin/settings/email-templates/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmailTemplateResponse updateEmailTemplate(@PathVariable String key,
            @Valid @RequestBody EmailTemplateRequest request) {
        return emailTemplateService.update(key, request);
    }

    @PostMapping("/admin/settings/email-templates/{key}/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public EmailTemplateResponse resetEmailTemplate(@PathVariable String key) {
        return emailTemplateService.reset(key);
    }

    @PostMapping("/admin/settings/email-templates/{key}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public void testEmailTemplate(@PathVariable String key, @Valid @RequestBody SmtpTestRequest request) {
        EmailTemplateService.RenderedEmail email = emailTemplateService.render(key, sampleVariables());
        if (!smtpSettingsService.sendEmail(request.getRecipientEmail(), email.getSubject(), email.getHtmlBody(), email.getTextBody())) {
            throw new IllegalStateException("SMTP is disabled");
        }
    }

    private Map<String, String> sampleVariables() {
        return Map.ofEntries(
                Map.entry("fullName", "HaoBox User"),
                Map.entry("logoUrl", "https://example.com/logo.png"),
                Map.entry("email", "user@example.com"),
                Map.entry("code", "123456"),
                Map.entry("expiresInMinutes", "30"),
                Map.entry("resetLink", "https://example.com/reset-password?token=sample"),
                Map.entry("verificationLink", "https://example.com/verify-email?token=sample"),
                Map.entry("ownerName", "Vo Hoan Hao"),
                Map.entry("ownerEmail", "owner@example.com"),
                Map.entry("fileName", "project-plan.pdf"),
                Map.entry("fileLink", "https://example.com/shared"),
                Map.entry("title", "Storage node health check failed"),
                Map.entry("message", "Node-1 could not be reached."),
                Map.entry("time", Instant.now().toString()),
                Map.entry("password", "TempPass123"),
                Map.entry("loginLink", "https://example.com/login"));
    }
}
