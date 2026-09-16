package cloud.haovo.filemanager.api.response;

import cloud.haovo.filemanager.domain.FileUploadSession;
import lombok.Value;

import java.util.Set;

@Value
public class ChunkUploadSessionResponse {
    String uploadId;
    int totalParts;
    Set<Integer> uploadedParts;
    String status;

    public static ChunkUploadSessionResponse from(FileUploadSession session) {
        return new ChunkUploadSessionResponse(
                session.getId(),
                session.getTotalParts(),
                session.uploadedPartSet(),
                session.getStatus());
    }
}
