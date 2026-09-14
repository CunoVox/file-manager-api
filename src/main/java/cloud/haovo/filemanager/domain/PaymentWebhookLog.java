package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_logs", indexes = {
        @Index(name = "idx_payment_webhook_logs_created", columnList = "created_at"),
        @Index(name = "idx_payment_webhook_logs_provider_order", columnList = "provider_order_code")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhookLog {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 30)
    private String provider = "PAYOS";

    @Column(name = "provider_order_code")
    private Long providerOrderCode;

    @Column(nullable = false, length = 30)
    private String status = "RECEIVED";

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}


