package cloud.haovo.filemanager.api;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class StorageNodeRequest {
    @NotBlank private String name;
    private String note;
    @NotBlank private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    @NotBlank private String bucket;
    private long capacityBytes;
    private long bandwidthLimitBytes;
    private int priority;
    private boolean enabled = true;
}
