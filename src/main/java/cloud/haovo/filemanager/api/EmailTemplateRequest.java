package cloud.haovo.filemanager.api;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@Setter
public class EmailTemplateRequest {
    @NotBlank
    @Size(max = 180)
    private String subject;

    @NotBlank
    private String htmlBody;

    @NotBlank
    private String textBody;
}
