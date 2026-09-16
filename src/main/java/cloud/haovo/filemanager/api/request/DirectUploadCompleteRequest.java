package cloud.haovo.filemanager.api.request;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class DirectUploadCompleteRequest {
    @Valid
    @NotEmpty
    private List<Part> parts;

    @Data
    public static class Part {
        @Min(1)
        private int partNumber;

        @NotBlank
        private String etag;
    }
}
