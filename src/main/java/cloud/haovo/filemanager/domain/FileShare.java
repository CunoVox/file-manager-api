package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_shares", uniqueConstraints = @UniqueConstraint(name = "uk_file_share_user", columnNames = {
        "file_id", "shared_with_user_id"
}))
@Getter
@Setter
@NoArgsConstructor
public class FileShare {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "shared_with_user_id", nullable = false, length = 36)
    private String sharedWithUserId;

    @Column(nullable = false, length = 20)
    private String permission = "VIEW";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
