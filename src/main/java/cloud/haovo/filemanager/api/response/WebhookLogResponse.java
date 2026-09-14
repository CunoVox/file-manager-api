package cloud.haovo.filemanager.api.response;

import cloud.haovo.filemanager.domain.PaymentWebhookLog;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class WebhookLogResponse {
    private String id;
    private String provider;
    private Long providerOrderCode;
    private String status;
    private String errorMessage;
    private String payloadJson;
    private Instant createdAt;

    public static WebhookLogResponse from(PaymentWebhookLog log) {
        return new WebhookLogResponse(log.getId(), log.getProvider(), log.getProviderOrderCode(),
                log.getStatus(), log.getErrorMessage(), log.getPayloadJson(), log.getCreatedAt());
    }
}
