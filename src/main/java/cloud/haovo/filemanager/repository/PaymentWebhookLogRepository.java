package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.PaymentWebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, String> {
    Page<PaymentWebhookLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

