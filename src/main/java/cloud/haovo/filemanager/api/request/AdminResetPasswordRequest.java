package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@Setter
public class AdminResetPasswordRequest {
    @NotBlank
    @Size(min = 8, max = 72)
    private String newPassword;
}
