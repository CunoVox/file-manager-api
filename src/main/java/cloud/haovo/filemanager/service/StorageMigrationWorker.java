package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.FileRecord;
import cloud.haovo.filemanager.domain.Folder;
import cloud.haovo.filemanager.domain.StorageMigrationJob;
import cloud.haovo.filemanager.domain.StorageMigrationStatus;
import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.FileRepository;
import cloud.haovo.filemanager.repository.FolderRepository;
import cloud.haovo.filemanager.repository.StorageMigrationJobRepository;
import cloud.haovo.filemanager.repository.StorageNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class StorageMigrationWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageMigrationWorker.class);

    private final StorageMigrationJobRepository jobs;
    private final StorageNodeRepository nodes;
    private final FileRepository files;
    private final FolderRepository folders;
    private final MinioStorageService storage;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public StorageMigrationWorker(StorageMigrationJobRepository jobs,
            StorageNodeRepository nodes,
            FileRepository files,
            FolderRepository folders,
            MinioStorageService storage,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.jobs = jobs;
        this.nodes = nodes;
        this.files = files;
        this.folders = folders;
        this.storage = storage;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Async
    public void run(String jobId) {
        StorageMigrationJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Migration job not found"));
        job.setStatus(StorageMigrationStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobs.save(job);

        try {
            StorageNode source = nodes.findById(job.getSourceNodeId())
                    .orElseThrow(() -> new IllegalArgumentException("Source storage node not found"));
            StorageNode target = nodes.findById(job.getTargetNodeId())
                    .orElseThrow(() -> new IllegalArgumentException("Target storage node not found"));

            migrateFiles(job, source, target);
            migrateFolders(job, source, target);

            job.setStatus(StorageMigrationStatus.COMPLETED);
            job.setFinishedAt(Instant.now());
            jobs.save(job);
            auditLogService.system("STORAGE_MIGRATION_COMPLETED", "STORAGE_MIGRATION", job.getId(),
                    source.getName() + " -> " + target.getName(),
                    "Completed storage migration from " + source.getName() + " to " + target.getName(),
                    Map.of("migratedFiles", job.getMigratedFiles(), "migratedFolders", job.getMigratedFolders(),
                            "migratedBytes", job.getMigratedBytes()));
            notificationService.notifyAdmins("STORAGE_MIGRATION_COMPLETED", "Migration completed",
                    "Migration from " + source.getName() + " to " + target.getName() + " completed.",
                    "STORAGE_MIGRATION", job.getId());
            log.info("Storage migration {} completed", jobId);
        } catch (Exception exception) {
            log.warn("Storage migration {} failed", jobId, exception);
            StorageMigrationJob failedJob = jobs.findById(jobId).orElse(job);
            failedJob.setStatus(StorageMigrationStatus.FAILED);
            failedJob.setErrorMessage(friendlyError(exception));
            failedJob.setFinishedAt(Instant.now());
            jobs.save(failedJob);
            auditLogService.system("STORAGE_MIGRATION_FAILED", "STORAGE_MIGRATION", failedJob.getId(),
                    "Migration " + failedJob.getId(), "Storage migration failed",
                    Map.of("error", friendlyError(exception)));
            notificationService.notifyAdmins("STORAGE_MIGRATION_FAILED", "Migration failed",
                    friendlyError(exception), "STORAGE_MIGRATION", failedJob.getId());
        }
    }

    private void migrateFiles(StorageMigrationJob job, StorageNode source, StorageNode target) {
        List<FileRecord> sourceFiles = files.findByStorageNodeIdOrderByCreatedAtAsc(source.getId());
        for (FileRecord file : sourceFiles) {
            if (!source.getId().equals(file.getStorageNodeId())) {
                continue;
            }
            String oldKey = file.getObjectKey();
            String newKey = storage.copyToNode(source, target, oldKey, file.getName(), file.getMimeType(), file.getSize());
            file.setStorageNodeId(target.getId());
            file.setObjectKey(newKey);
            try {
                files.save(file);
            } catch (RuntimeException exception) {
                storage.delete(target, newKey, file.getSize());
                throw exception;
            }
            storage.delete(source, oldKey, file.getSize());
            job.setMigratedFiles(job.getMigratedFiles() + 1);
            job.setMigratedBytes(job.getMigratedBytes() + file.getSize());
            jobs.save(job);
        }
    }

    private void migrateFolders(StorageMigrationJob job, StorageNode source, StorageNode target) {
        List<Folder> sourceFolders = folders.findByStorageNodeIdOrderByCreatedAtAsc(source.getId());
        for (Folder folder : sourceFolders) {
            if (!source.getId().equals(folder.getStorageNodeId())) {
                continue;
            }
            String oldKey = folder.getObjectKey();
            String newKey = storage.createFolder(target, folder.getName());
            folder.setStorageNodeId(target.getId());
            folder.setObjectKey(newKey);
            try {
                folders.save(folder);
            } catch (RuntimeException exception) {
                storage.delete(target, newKey, 0);
                throw exception;
            }
            if (oldKey != null) {
                storage.delete(source, oldKey, 0);
            }
            job.setMigratedFolders(job.getMigratedFolders() + 1);
            jobs.save(job);
        }
    }

    private String friendlyError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Migration failed. Please check both storage nodes and try again.";
        }
        if (message.length() > 1000) {
            return message.substring(0, 1000);
        }
        return message;
    }
}
