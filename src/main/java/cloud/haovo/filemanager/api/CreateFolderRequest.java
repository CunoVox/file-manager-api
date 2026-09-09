package cloud.haovo.filemanager.api;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CreateFolderRequest {
    @NotBlank
    private String name;
    private String parentId;
}