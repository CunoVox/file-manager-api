package cloud.haovo.filemanager.repository;

import cloud.haovo.filemanager.domain.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StorageNodeRepository extends JpaRepository<StorageNode, Long> {
    List<StorageNode> findByEnabledTrueOrderByPriorityAscIdAsc();

    @Query("select n from StorageNode n where n.enabled = true order by n.priority asc, (n.capacityBytes - n.usedBytes) desc, n.id asc")
    List<StorageNode> findUploadCandidates();
}
