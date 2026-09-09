package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
public class Folder {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "parent_id")
    private String parentId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "storage_node_id")
    private Long storageNodeId;

    @Column(name = "object_key", length = 500)
    private String objectKey;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
