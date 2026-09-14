package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
public class AssignPlanRequest {
    @NotNull
    private String userId;
    @NotNull
    private String planId;
}
