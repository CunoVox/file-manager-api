package cloud.haovo.filemanager.api;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {
    }

    @Data
    public static class Register {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        @Size(min = 8, max = 72)
        private String password;
        @NotBlank
        @Size(max = 120)
        private String fullName;
    }

    @Data
    public static class Login {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class Refresh {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class VerifyTwoFactor {
        @NotBlank
        private String challengeId;

        @NotBlank
        @Size(min = 6, max = 6)
        private String code;
    }

    @Data
    public static class VerifyEmail {
        @NotBlank
        private String token;
    }

    @Data
    public static class ResendVerification {
        @Email
        @NotBlank
        private String email;
    }

    @Data
    public static class ForgotPassword {
        @Email
        @NotBlank
        private String email;
    }

    @Data
    public static class ResetPassword {
        @NotBlank
        private String token;

        @NotBlank
        @Size(min = 8, max = 72)
        private String newPassword;
    }

    @Data
    public static class UpdateProfile {
        @NotBlank
        @Size(max = 120)
        private String fullName;
    }

    @Data
    public static class ChangePassword {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8, max = 72)
        private String newPassword;
    }

    @Data
    public static class UpdateTwoFactor {
        @NotBlank
        private String currentPassword;

        private boolean enabled;
    }
}
