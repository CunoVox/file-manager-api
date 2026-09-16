package cloud.haovo.filemanager.api.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ChunkUploadInitRequest {
    @NotBlank
    @Size(max = 255)
    private String fileName;

    @Size(max = 180)
    private String mimeType;

    @Min(1)
    private long size;

    @Min(1)
    private long chunkSize;

    @Min(1)
    private int totalParts;

    private String parentId;
    private Long storageNodeId;
}
