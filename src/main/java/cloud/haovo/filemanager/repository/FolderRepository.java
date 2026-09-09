package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.Folder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;

public interface FolderRepository extends JpaRepository<Folder, String> {
    List<Folder> findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(String ownerId);

    Page<Folder> findByOwnerIdAndParentIdAndDeletedAtIsNullOrderByNameAsc(String ownerId, String parentId, Pageable pageable);

    List<Folder> findByOwnerIdAndParentIdOrderByNameAsc(String ownerId, String parentId);

    Page<Folder> findByOwnerIdAndParentIdIsNullAndDeletedAtIsNullOrderByNameAsc(String ownerId, Pageable pageable);

    Page<Folder> findByOwnerIdAndParentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, String parentId, Pageable pageable);

    Page<Folder> findByOwnerIdAndParentIdIsNullAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, Pageable pageable);

    Page<Folder> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId, Pageable pageable);

    List<Folder> findByDeletedAtBeforeOrderByDeletedAtAsc(Instant deletedBefore);

    List<Folder> findByStorageNodeIdOrderByCreatedAtAsc(Long storageNodeId);

    @Modifying
    @Transactional
    @Query(value = "WITH RECURSIVE folder_tree AS (" +
            "SELECT id FROM folders WHERE id = :folderId AND owner_id = :ownerId " +
            "UNION ALL SELECT child.id FROM folders child " +
            "JOIN folder_tree parent ON child.parent_id = parent.id " +
            "WHERE child.owner_id = :ownerId) " +
            "DELETE FROM folders WHERE owner_id = :ownerId " +
            "AND id IN (SELECT id FROM folder_tree)", nativeQuery = true)
    int deleteTreeFolders(@Param("folderId") String folderId, @Param("ownerId") String ownerId);
}
