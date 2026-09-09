package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.User;
import lombok.Value;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Value
public class AdminUserResponse {
    String id;
    String email;
    String fullName;
    boolean enabled;
    Set<String> roles;
    Instant createdAt;
    Long storageQuotaBytes;
    Long effectiveStorageQuotaBytes;
    long storageUsedBytes;

    public static AdminUserResponse from(User user, Long effectiveStorageQuotaBytes, long storageUsedBytes) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.isEnabled(),
                user.safeRoles().stream().map(Enum::name).collect(Collectors.toSet()), user.getCreatedAt(),
                user.getStorageQuotaBytes(), effectiveStorageQuotaBytes, storageUsedBytes);
    }
}
