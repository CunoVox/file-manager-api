package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.FileShare;
import cloud.haovo.filemanager.domain.User;
import lombok.Value;

import java.time.Instant;

@Value
public class FileShareResponse {
    String id;
    String fileId;
    String userId;
    String email;
    String fullName;
    String permission;
    Instant createdAt;

    public static FileShareResponse from(FileShare share, User user) {
        return new FileShareResponse(share.getId(), share.getFileId(), user.getId(), user.getEmail(), user.getFullName(),
                share.getPermission(), share.getCreatedAt());
    }
}
