package cloud.haovo.filemanager.api;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class TrashPolicyRequest {
    @NotNull
    @Min(1)
    @Max(3650)
    private Integer retentionDays;
}
