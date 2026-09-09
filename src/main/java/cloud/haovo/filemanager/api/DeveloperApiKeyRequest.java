package cloud.haovo.filemanager.api;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class DeveloperApiKeyRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    private List<String> scopes;

    private Instant expiresAt;
}
