package cloud.haovo.filemanager.api;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AdminUserRequests {
    @Getter
    @Setter
    public static class Create {
        @Email
        @NotBlank
        private String email;

        @NotBlank
        @Size(max = 120)
        private String fullName;

        @NotBlank
        @Size(min = 6, max = 100)
        private String password;

        private String role = "MEMBER";

        private boolean enabled = true;

        private Long storageQuotaBytes;
    }

    @Getter
    @Setter
    public static class UpdateRole {
        @NotBlank
        private String role;
    }

    @Getter
    @Setter
    public static class UpdateStatus {
        private boolean enabled;
    }

    @Getter
    @Setter
    public static class UpdateQuota {
        private Long storageQuotaBytes;
    }

    @Getter
    @Setter
    public static class ResetPassword {
        @NotBlank
        @Size(min = 8, max = 72)
        private String newPassword;
    }
}
