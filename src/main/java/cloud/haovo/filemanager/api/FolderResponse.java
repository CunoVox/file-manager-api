package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.Folder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

import java.time.Instant;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FolderResponse {
    String id;
    String name;
    String parentId;
    Instant createdAt;
    Instant deletedAt;

    public static FolderResponse from(Folder folder) {
        return new FolderResponse(folder.getId(), folder.getName(), folder.getParentId(), folder.getCreatedAt(), folder.getDeletedAt());
    }
}
