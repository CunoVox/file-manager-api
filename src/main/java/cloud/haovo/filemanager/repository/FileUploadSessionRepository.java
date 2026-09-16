package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.FileUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FileUploadSessionRepository extends JpaRepository<FileUploadSession, String> {
    List<FileUploadSession> findByStatusAndUpdatedAtBefore(String status, Instant updatedAt);
}
