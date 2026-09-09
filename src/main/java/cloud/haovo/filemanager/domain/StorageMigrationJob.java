package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_migration_jobs")
@Getter
@Setter
@NoArgsConstructor
public class StorageMigrationJob {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "source_node_id", nullable = false)
    private Long sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    private Long targetNodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StorageMigrationStatus status = StorageMigrationStatus.PENDING;

    @Column(name = "total_files", nullable = false)
    private long totalFiles;

    @Column(name = "migrated_files", nullable = false)
    private long migratedFiles;

    @Column(name = "total_folders", nullable = false)
    private long totalFolders;

    @Column(name = "migrated_folders", nullable = false)
    private long migratedFolders;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "migrated_bytes", nullable = false)
    private long migratedBytes;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
