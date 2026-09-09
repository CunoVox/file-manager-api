package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.StorageMigrationJob;
import lombok.Value;

import java.time.Instant;

@Value
public class StorageMigrationJobResponse {
    String id;
    Long sourceNodeId;
    String sourceNodeName;
    Long targetNodeId;
    String targetNodeName;
    String status;
    long totalFiles;
    long migratedFiles;
    long totalFolders;
    long migratedFolders;
    long totalBytes;
    long migratedBytes;
    String errorMessage;
    Instant createdAt;
    Instant startedAt;
    Instant finishedAt;

    public static StorageMigrationJobResponse from(StorageMigrationJob job, String sourceNodeName, String targetNodeName) {
        return new StorageMigrationJobResponse(job.getId(), job.getSourceNodeId(), sourceNodeName,
                job.getTargetNodeId(), targetNodeName, job.getStatus().name(), job.getTotalFiles(),
                job.getMigratedFiles(), job.getTotalFolders(), job.getMigratedFolders(), job.getTotalBytes(),
                job.getMigratedBytes(), job.getErrorMessage(), job.getCreatedAt(), job.getStartedAt(),
                job.getFinishedAt());
    }
}
