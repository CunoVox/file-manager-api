package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.DeveloperApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeveloperApiKeyRepository extends JpaRepository<DeveloperApiKey, String> {
    List<DeveloperApiKey> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Page<DeveloperApiKey> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable);

    Optional<DeveloperApiKey> findByKeyHash(String keyHash);
}
