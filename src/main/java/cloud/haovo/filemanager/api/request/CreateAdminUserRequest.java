package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@Setter
public class CreateAdminUserRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    private String role = "MEMBER";

    private boolean enabled = true;

    private Long storageQuotaBytes;
}
