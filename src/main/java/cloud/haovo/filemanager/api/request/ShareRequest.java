package cloud.haovo.filemanager.api.request;

import lombok.Data;

@Data
public class ShareRequest {
    private String email;
    private String permission = "VIEW";
}


