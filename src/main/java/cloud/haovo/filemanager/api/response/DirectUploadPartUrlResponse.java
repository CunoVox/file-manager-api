package cloud.haovo.filemanager.api.response;

import lombok.Value;

@Value
public class DirectUploadPartUrlResponse {
    int partNumber;
    String url;
}
