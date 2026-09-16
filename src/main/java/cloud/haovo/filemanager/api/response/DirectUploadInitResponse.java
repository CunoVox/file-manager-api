package cloud.haovo.filemanager.api.response;

import cloud.haovo.filemanager.domain.FileUploadSession;
import lombok.Value;

@Value
public class DirectUploadInitResponse {
    String uploadId;
    int totalParts;
    long partSize;
    String status;

    public static DirectUploadInitResponse from(FileUploadSession session) {
        return new DirectUploadInitResponse(
                session.getId(),
                session.getTotalParts(),
                session.getChunkSize(),
                session.getStatus());
    }
}
