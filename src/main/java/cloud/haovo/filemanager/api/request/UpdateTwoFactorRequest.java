package cloud.haovo.filemanager.api.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UpdateTwoFactorRequest {
    @NotBlank
    private String currentPassword;

    private boolean enabled;
}
