package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.StorageNode;
import lombok.Value;

import java.time.Instant;

@Value
public class StorageNodeResponse {
    Long id; String name; String note; String endpoint; String region; String bucket; long capacityBytes;
    long bandwidthLimitBytes; long bandwidthUsedBytes; Instant bandwidthResetAt; long usedBytes;
    boolean enabled; int priority; Instant lastCheckedAt; String lastError;

    public static StorageNodeResponse from(StorageNode node) {
        return new StorageNodeResponse(node.getId(), node.getName(), node.getNote(), node.getEndpoint(), node.getRegion(), node.getBucket(), node.getCapacityBytes(), node.getBandwidthLimitBytes(), node.getBandwidthUsedBytes(), node.getBandwidthResetAt(), node.getUsedBytes(), node.isEnabled(), node.getPriority(), node.getLastCheckedAt(), node.getLastError());
    }
}
