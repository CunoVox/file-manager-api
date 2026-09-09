package cloud.haovo.filemanager.api;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BulkDownloadRequest {
    private List<String> fileIds = new ArrayList<>();
}
