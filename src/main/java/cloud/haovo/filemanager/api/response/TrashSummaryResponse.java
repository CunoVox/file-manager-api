package cloud.haovo.filemanager.api.response;

import lombok.Value;

@Value
public class TrashSummaryResponse {
    long itemCount;
    long totalBytes;
}
