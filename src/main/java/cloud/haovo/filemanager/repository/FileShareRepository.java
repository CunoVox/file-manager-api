package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FileShareRepository extends JpaRepository<FileShare, String> {
    List<FileShare> findByFileIdOrderByCreatedAtDesc(String fileId);

    List<FileShare> findBySharedWithUserIdOrderByCreatedAtDesc(String sharedWithUserId);

    Optional<FileShare> findByFileIdAndSharedWithUserId(String fileId, String sharedWithUserId);

    boolean existsByFileIdAndSharedWithUserId(String fileId, String sharedWithUserId);

    @Transactional
    void deleteByFileId(String fileId);

    void deleteByFileIdAndSharedWithUserId(String fileId, String sharedWithUserId);
}
