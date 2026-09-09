package cloud.haovo.filemanager.api;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class SmtpTestRequest {
    @Email
    @NotBlank
    private String recipientEmail;
}
