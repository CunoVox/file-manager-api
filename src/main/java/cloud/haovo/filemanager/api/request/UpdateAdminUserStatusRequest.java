package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAdminUserStatusRequest {
    private boolean enabled;
}
