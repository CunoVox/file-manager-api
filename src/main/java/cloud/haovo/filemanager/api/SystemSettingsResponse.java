package cloud.haovo.filemanager.api;

import lombok.Value;

@Value
public class SystemSettingsResponse {
    TrashPolicyResponse trashPolicy;
    SmtpSettingsResponse smtp;
}
