package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAdminUserQuotaRequest {
    private Long storageQuotaBytes;
}
