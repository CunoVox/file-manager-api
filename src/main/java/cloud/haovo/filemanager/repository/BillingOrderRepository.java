package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.BillingOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingOrderRepository extends JpaRepository<BillingOrder, String> {
    Optional<BillingOrder> findByProviderOrderCode(long providerOrderCode);
    Page<BillingOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<BillingOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<BillingOrder> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    long countByStatus(String status);
}

