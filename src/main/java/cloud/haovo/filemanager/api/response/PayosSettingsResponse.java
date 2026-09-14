package cloud.haovo.filemanager.api.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PayosSettingsResponse {
    private boolean enabled;
    private String clientId;
    private boolean apiKeyConfigured;
    private boolean checksumKeyConfigured;
    private String returnUrl;
    private String cancelUrl;
    private String webhookUrl;
    private boolean encryptionConfigured;
}
