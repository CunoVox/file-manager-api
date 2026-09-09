package cloud.haovo.filemanager.api;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@Getter
@Setter
public class SmtpSettingsRequest {
    private boolean enabled;

    @Size(max = 255)
    private String host;

    @Min(1)
    @Max(65535)
    private int port = 587;

    @Size(max = 255)
    private String username;

    @Size(max = 500)
    private String password;

    private boolean startTls = true;

    private boolean auth = true;

    @Size(max = 255)
    private String fromEmail;

    @Size(max = 120)
    private String fromName = "HaoBox";

    @Size(max = 180)
    private String otpSubject = "Your HaoBox verification code";
}
