package cloud.haovo.filemanager.api.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
public class BillingPlanRequest {
    @NotBlank
    private String name;
    @Min(1)
    private long quotaGb;
    @Min(0)
    private long price;
    private String currency = "VND";
    @Min(0)
    private int durationDays = 30;
    private String description;
    private boolean active = true;
    private int sortOrder;
}
