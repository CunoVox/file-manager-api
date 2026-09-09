package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.TrashPolicyRequest;
import cloud.haovo.filemanager.api.TrashPolicyResponse;
import cloud.haovo.filemanager.domain.AppSetting;
import cloud.haovo.filemanager.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class TrashPolicyService {
    private static final String RETENTION_DAYS_KEY = "trash.retentionDays";

    private final AppSettingRepository settings;
    private final AuditLogService auditLogService;
    private final int defaultRetentionDays;

    public TrashPolicyService(AppSettingRepository settings, AuditLogService auditLogService,
            @Value("${app.trash.retention-days:30}") int defaultRetentionDays) {
        this.settings = settings;
        this.auditLogService = auditLogService;
        this.defaultRetentionDays = Math.max(1, defaultRetentionDays);
    }

    @Transactional(readOnly = true)
    public TrashPolicyResponse getPolicy() {
        return new TrashPolicyResponse(retentionDays());
    }

    @Transactional(readOnly = true)
    public int retentionDays() {
        return settings.findById(RETENTION_DAYS_KEY)
                .map(AppSetting::getValue)
                .map(this::parseRetentionDays)
                .orElse(defaultRetentionDays);
    }

    @Transactional
    public TrashPolicyResponse updatePolicy(TrashPolicyRequest request) {
        int retentionDays = request.getRetentionDays();
        AppSetting setting = settings.findById(RETENTION_DAYS_KEY)
                .orElseGet(() -> new AppSetting(RETENTION_DAYS_KEY, String.valueOf(retentionDays)));
        setting.setValue(String.valueOf(retentionDays));
        setting.setUpdatedAt(Instant.now());
        settings.save(setting);
        auditLogService.record("TRASH_POLICY_UPDATED", "SETTING", RETENTION_DAYS_KEY, "Recycle Bin Policy",
                "Updated Trash retention to " + retentionDays + " days",
                Map.of("retentionDays", retentionDays));
        return new TrashPolicyResponse(retentionDays);
    }

    private int parseRetentionDays(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (RuntimeException exception) {
            return defaultRetentionDays;
        }
    }
}
