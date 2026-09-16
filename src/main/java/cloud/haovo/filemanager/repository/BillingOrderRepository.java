package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.BillingOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BillingOrderRepository extends JpaRepository<BillingOrder, String> {
    Optional<BillingOrder> findByProviderOrderCode(long providerOrderCode);
    Page<BillingOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<BillingOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<BillingOrder> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    List<BillingOrder> findByStatusAndCreatedAtBefore(String status, Instant createdBefore);
    List<BillingOrder> findByUserIdAndStatus(String userId, String status);
    long countByStatus(String status);
}

