package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_parent", columnList = "parent_id"),
        @Index(name = "idx_files_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
public class FileRecord {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "storage_node_id")
    private Long storageNodeId;

    @Column(name = "object_key", length = 500)
    private String objectKey;

    @Column(name = "parent_id")
    private String parentId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "mime_type", nullable = false, length = 180)
    private String mimeType;

    @Column(nullable = false)
    private long size;

    @Column(length = 20, nullable = false)
    private String visibility = "PUBLIC";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
