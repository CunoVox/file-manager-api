package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, String> {
    List<BillingPlan> findByActiveTrueOrderBySortOrderAscCreatedAtAsc();
    List<BillingPlan> findAllByOrderBySortOrderAscCreatedAtAsc();
}
