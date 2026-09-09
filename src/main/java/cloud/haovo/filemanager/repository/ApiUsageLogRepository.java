package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.ApiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, String> {
    Page<ApiUsageLog> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable);
}
