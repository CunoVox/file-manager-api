package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.EmailToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, String> {
    Optional<EmailToken> findByIdAndTypeAndConsumedAtIsNull(String id, String type);
}
