package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class UpdateAdminUserRoleRequest {
    @NotBlank
    private String role;
}
