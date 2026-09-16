package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.FileUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FileUploadSessionRepository extends JpaRepository<FileUploadSession, String> {
    List<FileUploadSession> findByStatusAndUpdatedAtBefore(String status, Instant updatedAt);

    List<FileUploadSession> findByOwnerIdAndStatus(String ownerId, String status);

    @Query("select coalesce(sum(session.size), 0) from FileUploadSession session where session.ownerId = :ownerId and session.status = 'UPLOADING'")
    long sumUploadingSizeByOwnerId(@Param("ownerId") String ownerId);

    @Query("select coalesce(sum(session.size), 0) from FileUploadSession session where session.ownerId = :ownerId and session.status = 'UPLOADING' and session.updatedAt >= :updatedAfter")
    long sumActiveUploadingSizeByOwnerId(@Param("ownerId") String ownerId, @Param("updatedAfter") Instant updatedAfter);
}
