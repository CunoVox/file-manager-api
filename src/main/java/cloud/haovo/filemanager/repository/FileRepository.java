package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.FileRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;

public interface FileRepository extends JpaRepository<FileRecord, String> {
    Page<FileRecord> findByOwnerIdAndParentIdAndDeletedAtIsNullOrderByNameAsc(String ownerId, String parentId, Pageable pageable);

    List<FileRecord> findByOwnerIdAndParentIdOrderByNameAsc(String ownerId, String parentId);

    Page<FileRecord> findByOwnerIdAndParentIdIsNullAndDeletedAtIsNullOrderByNameAsc(String ownerId, Pageable pageable);

    Page<FileRecord> findByOwnerIdAndParentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, String parentId, Pageable pageable);

    Page<FileRecord> findByOwnerIdAndParentIdIsNullAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, Pageable pageable);

    Page<FileRecord> findByOwnerIdAndNameContainingIgnoreCaseAndDeletedAtIsNullOrderByNameAsc(String ownerId, String keyword, Pageable pageable);

    Page<FileRecord> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, Pageable pageable);

    List<FileRecord> findByOwnerIdAndParentIdAndDeletedAtIsNullOrderByNameAsc(String ownerId, String parentId);

    List<FileRecord> findByDeletedAtBeforeOrderByDeletedAtAsc(Instant deletedBefore);

    List<FileRecord> findByStorageNodeIdOrderByCreatedAtAsc(Long storageNodeId);

    @Query("select coalesce(sum(file.size), 0) from FileRecord file where file.ownerId = :ownerId")
    long sumSizeByOwnerId(@Param("ownerId") String ownerId);

    @Modifying
    @Transactional
    @Query(value = "WITH RECURSIVE folder_tree AS (" +
            "SELECT id FROM folders WHERE id = :folderId AND owner_id = :ownerId " +
            "UNION ALL SELECT child.id FROM folders child " +
            "JOIN folder_tree parent ON child.parent_id = parent.id " +
            "WHERE child.owner_id = :ownerId) " +
            "DELETE FROM files WHERE owner_id = :ownerId " +
            "AND parent_id IN (SELECT id FROM folder_tree)", nativeQuery = true)
    int deleteTreeFiles(@Param("folderId") String folderId, @Param("ownerId") String ownerId);
}
