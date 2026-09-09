package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.TwoFactorChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, String> {
    Optional<TwoFactorChallenge> findByIdAndConsumedAtIsNull(String id);
}
