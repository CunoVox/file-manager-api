package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.StorageMigrationJobResponse;
import cloud.haovo.filemanager.domain.FileRecord;
import cloud.haovo.filemanager.domain.StorageMigrationJob;
import cloud.haovo.filemanager.domain.StorageMigrationStatus;
import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.FileRepository;
import cloud.haovo.filemanager.repository.FolderRepository;
import cloud.haovo.filemanager.repository.StorageMigrationJobRepository;
import cloud.haovo.filemanager.repository.StorageNodeRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StorageMigrationService {
    private final StorageMigrationJobRepository jobs;
    private final StorageNodeRepository nodes;
    private final FileRepository files;
    private final FolderRepository folders;
    private final MinioStorageService storage;
    private final StorageMigrationWorker worker;
    private final AuditLogService auditLogService;

    public StorageMigrationService(StorageMigrationJobRepository jobs,
            StorageNodeRepository nodes,
            FileRepository files,
            FolderRepository folders,
            MinioStorageService storage,
            StorageMigrationWorker worker,
            AuditLogService auditLogService) {
        this.jobs = jobs;
        this.nodes = nodes;
        this.files = files;
        this.folders = folders;
        this.storage = storage;
        this.worker = worker;
        this.auditLogService = auditLogService;
    }

    public List<StorageMigrationJobResponse> listJobs() {
        return jobs.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public StorageMigrationJobResponse startMigration(Long sourceNodeId, Long targetNodeId) {
        if (sourceNodeId.equals(targetNodeId)) {
            throw new IllegalArgumentException("Source and target storage nodes must be different");
        }
        List<StorageMigrationStatus> activeStatuses = Arrays.asList(StorageMigrationStatus.PENDING, StorageMigrationStatus.RUNNING);
        if (jobs.existsBySourceNodeIdAndStatusIn(sourceNodeId, activeStatuses)
                || jobs.existsByTargetNodeIdAndStatusIn(sourceNodeId, activeStatuses)) {
            throw new IllegalStateException("This source node already has a running migration");
        }
        if (jobs.existsBySourceNodeIdAndStatusIn(targetNodeId, activeStatuses)
                || jobs.existsByTargetNodeIdAndStatusIn(targetNodeId, activeStatuses)) {
            throw new IllegalStateException("This target node already has a running migration");
        }

        StorageNode source = nodes.findById(sourceNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Source storage node not found"));
        StorageNode target = nodes.findById(targetNodeId)
                .filter(StorageNode::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Target storage node not found or disabled"));
        List<FileRecord> sourceFiles = files.findByStorageNodeIdOrderByCreatedAtAsc(source.getId());
        long totalBytes = sourceFiles.stream().mapToLong(FileRecord::getSize).sum();
        if (sourceFiles.isEmpty() && folders.findByStorageNodeIdOrderByCreatedAtAsc(source.getId()).isEmpty()) {
            throw new IllegalArgumentException("There are no files or folders to migrate from this node");
        }
        storage.requireCapacity(target, totalBytes);
        storage.test(source);
        storage.test(target);

        StorageMigrationJob job = new StorageMigrationJob();
        job.setSourceNodeId(source.getId());
        job.setTargetNodeId(target.getId());
        job.setTotalFiles(sourceFiles.size());
        job.setTotalFolders(folders.findByStorageNodeIdOrderByCreatedAtAsc(source.getId()).size());
        job.setTotalBytes(totalBytes);
        job = jobs.save(job);
        auditLogService.record("STORAGE_MIGRATION_STARTED", "STORAGE_MIGRATION", job.getId(),
                source.getName() + " -> " + target.getName(),
                "Started storage migration from " + source.getName() + " to " + target.getName(),
                Map.of("sourceNodeId", source.getId(), "targetNodeId", target.getId(),
                        "totalFiles", job.getTotalFiles(), "totalFolders", job.getTotalFolders(),
                        "totalBytes", job.getTotalBytes()));
        worker.run(job.getId());
        return toResponse(job);
    }

    private StorageMigrationJobResponse toResponse(StorageMigrationJob job) {
        String sourceName = nodes.findById(job.getSourceNodeId()).map(StorageNode::getName).orElse("Deleted node");
        String targetName = nodes.findById(job.getTargetNodeId()).map(StorageNode::getName).orElse("Deleted node");
        return StorageMigrationJobResponse.from(job, sourceName, targetName);
    }
}
