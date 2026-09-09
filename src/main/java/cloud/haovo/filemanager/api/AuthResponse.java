package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.User;
import lombok.Value;

import java.util.Set;
import java.util.stream.Collectors;

@Value
public class AuthResponse {
    String accessToken;
    String refreshToken;
    UserResponse user;
    boolean twoFactorRequired;
    String twoFactorToken;
    boolean emailVerificationRequired;
    String email;

    public static AuthResponse session(String accessToken, String refreshToken, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, user, false, null, false, null);
    }

    public static AuthResponse twoFactorRequired(String twoFactorToken) {
        return new AuthResponse(null, null, null, true, twoFactorToken, false, null);
    }

    public static AuthResponse emailVerificationRequired(String email) {
        return new AuthResponse(null, null, null, false, null, true, email);
    }

    @Value
    public static class UserResponse {
        String id;
        String email;
        String fullName;
        Set<String> roles;
        Long storageQuotaBytes;
        Long effectiveStorageQuotaBytes;
        long storageUsedBytes;
        boolean twoFactorEnabled;
        boolean emailVerified;

        public static UserResponse from(User user, Long effectiveStorageQuotaBytes, long storageUsedBytes) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                    user.safeRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                    user.getStorageQuotaBytes(), effectiveStorageQuotaBytes, storageUsedBytes,
                    user.isTwoFactorEnabled(), user.isEmailVerified());
        }
    }
}
