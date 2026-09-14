package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, String> {
    Optional<UserSubscription> findFirstByUserIdAndActiveTrueOrderByStartsAtDesc(String userId);
    List<UserSubscription> findByUserIdOrderByStartsAtDesc(String userId);
    List<UserSubscription> findByActiveTrueAndExpiresAtBefore(Instant now);
    List<UserSubscription> findByActiveTrueAndExpiresAtBetween(Instant from, Instant to);
    long countByActiveTrue();
}

