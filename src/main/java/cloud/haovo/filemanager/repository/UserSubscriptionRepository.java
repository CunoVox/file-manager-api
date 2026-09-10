package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, String> {
    Optional<UserSubscription> findFirstByUserIdAndActiveTrueOrderByStartsAtDesc(String userId);
}
