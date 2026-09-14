package cloud.haovo.filemanager.api.response;

import lombok.Value;

@Value
public class SystemSettingsResponse {
    TrashPolicyResponse trashPolicy;
    SmtpSettingsResponse smtp;
}


