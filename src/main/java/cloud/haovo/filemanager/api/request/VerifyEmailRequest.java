package cloud.haovo.filemanager.api.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class VerifyEmailRequest {
    @NotBlank
    private String token;
}
