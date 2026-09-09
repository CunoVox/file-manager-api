package cloud.haovo.filemanager.api;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class StorageMigrationRequest {
    @NotNull
    private Long targetNodeId;
}
