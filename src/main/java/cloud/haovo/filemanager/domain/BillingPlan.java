package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_plans")
@Getter
@Setter
@NoArgsConstructor
public class BillingPlan {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false, length = 10)
    private String currency = "VND";

    @Column(name = "duration_days", nullable = false)
    private int durationDays = 30;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
