package cloud.haovo.filemanager.api.request;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@Data
public class ResendVerificationRequest {
    @Email
    @NotBlank
    private String email;
}
