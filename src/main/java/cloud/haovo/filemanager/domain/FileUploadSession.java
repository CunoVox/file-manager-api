package cloud.haovo.filemanager.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "file_upload_sessions")
@Getter
@Setter
@NoArgsConstructor
public class FileUploadSession {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String ownerId;

    @Column(length = 36)
    private String parentId;

    @Column(nullable = false)
    private Long storageNodeId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 180)
    private String mimeType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private long chunkSize;

    @Column(nullable = false)
    private int totalParts;

    @Column(nullable = false, length = 30)
    private String status = "UPLOADING";

    @Column(nullable = false, length = 30)
    private String uploadMode = "SERVER_CHUNK";

    @Column(length = 800)
    private String objectKey;

    @Column(length = 1000)
    private String multipartUploadId;

    @Column(length = 2000)
    private String uploadedParts = "";

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Set<Integer> uploadedPartSet() {
        if (uploadedParts == null || uploadedParts.trim().isEmpty()) {
            return new java.util.HashSet<>();
        }
        return Arrays.stream(uploadedParts.split(","))
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    public void setUploadedPartSet(Set<Integer> parts) {
        uploadedParts = parts.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
