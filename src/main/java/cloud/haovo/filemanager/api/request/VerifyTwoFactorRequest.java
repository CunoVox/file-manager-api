package cloud.haovo.filemanager.api.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class VerifyTwoFactorRequest {
    @NotBlank
    private String challengeId;

    @NotBlank
    @Size(min = 6, max = 6)
    private String code;
}
