package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_orders", indexes = {
        @Index(name = "idx_billing_orders_user", columnList = "user_id"),
        @Index(name = "idx_billing_orders_provider_order", columnList = "provider_order_code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class BillingOrder {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "plan_id", nullable = false, length = 36)
    private String planId;

    @Column(name = "plan_name", nullable = false, length = 120)
    private String planName;

    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 10)
    private String currency = "VND";

    @Column(nullable = false, length = 30)
    private String provider = "PAYOS";

    @Column(name = "provider_order_code", nullable = false)
    private long providerOrderCode;

    @Column(name = "payment_link_id", length = 120)
    private String paymentLinkId;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
