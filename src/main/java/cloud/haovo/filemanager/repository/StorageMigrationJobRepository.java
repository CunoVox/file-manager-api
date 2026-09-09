package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.StorageMigrationJob;
import cloud.haovo.filemanager.domain.StorageMigrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorageMigrationJobRepository extends JpaRepository<StorageMigrationJob, String> {
    List<StorageMigrationJob> findTop20ByOrderByCreatedAtDesc();

    boolean existsBySourceNodeIdAndStatusIn(Long sourceNodeId, List<StorageMigrationStatus> statuses);

    boolean existsByTargetNodeIdAndStatusIn(Long targetNodeId, List<StorageMigrationStatus> statuses);
}
