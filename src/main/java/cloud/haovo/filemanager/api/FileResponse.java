package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.FileRecord;
import cloud.haovo.filemanager.domain.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

import java.time.Instant;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileResponse {
    String id;
    String name;
    String mimeType;
    long size;
    String parentId;
    String visibility;
    String viewUrl;
    String downloadUrl;
    Instant createdAt;
    Instant deletedAt;
    String ownerName;
    String ownerEmail;

    public static FileResponse from(FileRecord file, String baseUrl) {
        return from(file, baseUrl, null);
    }

    public static FileResponse from(FileRecord file, String baseUrl, User owner) {
        return new FileResponse(file.getId(), file.getName(), file.getMimeType(), file.getSize(),
                file.getParentId(), file.getVisibility(), baseUrl + "/view/" + file.getId(),
                baseUrl + "/download/" + file.getId(), file.getCreatedAt(), file.getDeletedAt(),
                owner == null ? null : owner.getFullName(), owner == null ? null : owner.getEmail());
    }
}
