package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserRole;
import cloud.haovo.filemanager.repository.FileRepository;
import org.springframework.stereotype.Service;

@Service
public class UserQuotaService {
    public static final long MEMBER_DEFAULT_QUOTA_BYTES = 5L * 1024 * 1024 * 1024;
    public static final long UNLIMITED_QUOTA = -1L;

    private final FileRepository files;

    public UserQuotaService(FileRepository files) {
        this.files = files;
    }

    public long usedBytes(User user) {
        return files.sumSizeByOwnerId(user.getId());
    }

    public Long effectiveQuotaBytes(User user) {
        Long configured = user.getStorageQuotaBytes();
        if (configured != null) {
            return configured < 0 ? null : configured;
        }
        return user.safeRoles().contains(UserRole.ADMIN) ? null : MEMBER_DEFAULT_QUOTA_BYTES;
    }

    public void requireAvailable(User user, long incomingBytes) {
        Long quota = effectiveQuotaBytes(user);
        if (quota == null) return;
        long used = usedBytes(user);
        if (incomingBytes > Math.max(0, quota - used)) {
            throw new IllegalStateException("USER_STORAGE_QUOTA_EXCEEDED");
        }
    }
}
