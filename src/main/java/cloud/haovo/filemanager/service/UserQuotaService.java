package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserRole;
import cloud.haovo.filemanager.repository.FileRepository;
import cloud.haovo.filemanager.repository.FileUploadSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class UserQuotaService {
    public static final long MEMBER_DEFAULT_QUOTA_BYTES = 5L * 1024 * 1024 * 1024;
    public static final long UNLIMITED_QUOTA = -1L;

    private final FileRepository files;
    private final FileUploadSessionRepository uploadSessions;
    private final Duration uploadReservationTimeout;

    public UserQuotaService(FileRepository files, FileUploadSessionRepository uploadSessions,
            @Value("${app.upload.session-timeout-hours:24}") long uploadSessionTimeoutHours) {
        this.files = files;
        this.uploadSessions = uploadSessions;
        this.uploadReservationTimeout = Duration.ofHours(uploadSessionTimeoutHours);
    }

    public long usedBytes(User user) {
        return files.sumSizeByOwnerId(user.getId());
    }

    public long reservedUploadBytes(User user) {
        Instant activeAfter = Instant.now().minus(uploadReservationTimeout);
        return uploadSessions.sumActiveUploadingSizeByOwnerId(user.getId(), activeAfter);
    }

    public Long effectiveQuotaBytes(User user) {
        Long configured = user.getStorageQuotaBytes();
        if (configured != null) {
            return configured < 0 ? null : configured;
        }
        return user.safeRoles().contains(UserRole.ADMIN) ? null : MEMBER_DEFAULT_QUOTA_BYTES;
    }

    public synchronized void requireAvailable(User user, long incomingBytes) {
        Long quota = effectiveQuotaBytes(user);
        if (quota == null) return;
        long committedAndReserved = usedBytes(user) + reservedUploadBytes(user);
        if (incomingBytes > Math.max(0, quota - committedAndReserved)) {
            throw new IllegalStateException("USER_STORAGE_QUOTA_EXCEEDED");
        }
    }
}

