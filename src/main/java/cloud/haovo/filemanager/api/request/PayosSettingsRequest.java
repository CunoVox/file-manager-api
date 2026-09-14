package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PayosSettingsRequest {
    private boolean enabled;
    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String returnUrl;
    private String cancelUrl;
}
