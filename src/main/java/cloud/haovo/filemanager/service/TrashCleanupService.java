package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.FileRecord;
import cloud.haovo.filemanager.domain.Folder;
import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.FileRepository;
import cloud.haovo.filemanager.repository.FileShareRepository;
import cloud.haovo.filemanager.repository.FolderRepository;
import cloud.haovo.filemanager.repository.StorageNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrashCleanupService {
    private static final Logger log = LoggerFactory.getLogger(TrashCleanupService.class);

    private final FileRepository fileRepository;
    private final FileShareRepository fileShareRepository;
    private final FolderRepository folderRepository;
    private final StorageNodeRepository storageNodes;
    private final MinioStorageService storage;
    private final TrashPolicyService trashPolicyService;

    public TrashCleanupService(FileRepository fileRepository,
            FileShareRepository fileShareRepository,
            FolderRepository folderRepository,
            StorageNodeRepository storageNodes,
            MinioStorageService storage,
            TrashPolicyService trashPolicyService) {
        this.fileRepository = fileRepository;
        this.fileShareRepository = fileShareRepository;
        this.folderRepository = folderRepository;
        this.storageNodes = storageNodes;
        this.storage = storage;
        this.trashPolicyService = trashPolicyService;
    }

    @Transactional
    @Scheduled(cron = "${app.trash.cleanup-cron:0 15 2 * * *}")
    public void cleanupExpiredTrash() {
        int retentionDays = trashPolicyService.retentionDays();
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int foldersDeleted = 0;
        int filesDeleted = 0;
        try {
            for (Folder folder : folderRepository.findByDeletedAtBeforeOrderByDeletedAtAsc(cutoff)) {
                if (folderRepository.existsById(folder.getId()) && !hasDeletedAncestor(folder)) {
                    purgeFolder(folder);
                    foldersDeleted++;
                }
            }
            for (FileRecord file : fileRepository.findByDeletedAtBeforeOrderByDeletedAtAsc(cutoff)) {
                if (fileRepository.existsById(file.getId())) {
                    purgeFile(file);
                    filesDeleted++;
                }
            }
            if (foldersDeleted > 0 || filesDeleted > 0) {
                log.info("Trash cleanup deleted {} folders and {} files older than {} days",
                        foldersDeleted, filesDeleted, retentionDays);
            }
        } catch (Exception exception) {
            log.warn("Trash cleanup failed", exception);
        }
    }

    private boolean hasDeletedAncestor(Folder folder) {
        String parentId = folder.getParentId();
        while (parentId != null) {
            Folder parent = folderRepository.findById(parentId).orElse(null);
            if (parent == null) {
                return false;
            }
            if (parent.getDeletedAt() != null) {
                return true;
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    @Transactional
    public void purgeFile(FileRecord file) {
        StorageNode fileNode = storageNodes.findById(file.getStorageNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        storage.delete(fileNode, file.getObjectKey(), file.getSize());
        fileShareRepository.deleteByFileId(file.getId());
        fileRepository.delete(file);
    }

    @Transactional
    public void purgeFolder(Folder folder) {
        List<String> folderIds = new ArrayList<>();
        collectFolderIds(folder, folderIds);
        for (String folderId : folderIds) {
            for (FileRecord file : fileRepository.findByOwnerIdAndParentIdOrderByNameAsc(folder.getOwnerId(), folderId)) {
                StorageNode fileNode = storageNodes.findById(file.getStorageNodeId())
                        .orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
                storage.delete(fileNode, file.getObjectKey(), file.getSize());
                fileShareRepository.deleteByFileId(file.getId());
            }
        }
        for (String folderId : folderIds) {
            Folder storedFolder = folderRepository.findById(folderId).orElse(null);
            if (storedFolder != null && storedFolder.getObjectKey() != null) {
                StorageNode folderNode = storageNodes.findById(storedFolder.getStorageNodeId())
                        .orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
                storage.delete(folderNode, storedFolder.getObjectKey(), 0);
            }
        }
        fileRepository.deleteTreeFiles(folder.getId(), folder.getOwnerId());
        folderRepository.deleteTreeFolders(folder.getId(), folder.getOwnerId());
    }

    private void collectFolderIds(Folder folder, List<String> folderIds) {
        folderIds.add(folder.getId());
        for (Folder child : folderRepository.findByOwnerIdAndParentIdOrderByNameAsc(folder.getOwnerId(), folder.getId())) {
            collectFolderIds(child, folderIds);
        }
    }
}
