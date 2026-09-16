package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.FileUploadSession;
import cloud.haovo.filemanager.repository.FileUploadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

@Service
public class FileUploadSessionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(FileUploadSessionCleanupService.class);
    private static final Path CHUNK_UPLOAD_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "haobox-upload-parts");

    private final FileUploadSessionRepository uploadSessionRepository;
    private final Duration timeout;

    public FileUploadSessionCleanupService(FileUploadSessionRepository uploadSessionRepository,
            @Value("${app.upload.session-timeout-hours:24}") long timeoutHours) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.timeout = Duration.ofHours(timeoutHours);
    }

    @Transactional
    @Scheduled(cron = "${app.upload.cleanup-cron:0 */30 * * * *}")
    public void cleanupStaleUploads() {
        Instant cutoff = Instant.now().minus(timeout);
        int cleaned = 0;
        try {
            for (FileUploadSession session : uploadSessionRepository.findByStatusAndUpdatedAtBefore("UPLOADING", cutoff)) {
                session.setStatus("ABORTED");
                uploadSessionRepository.save(session);
                cleanupUploadParts(session.getId());
                cleaned++;
            }
            if (cleaned > 0) {
                log.info("Cleaned {} stale upload sessions", cleaned);
            }
        } catch (Exception exception) {
            log.warn("Upload session cleanup failed", exception);
        }
    }

    private void cleanupUploadParts(String uploadId) throws IOException {
        Path directory = CHUNK_UPLOAD_ROOT.resolve(uploadId).normalize();
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not delete upload temp file", exception);
                }
            });
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException) {
                throw (IOException) exception.getCause();
            }
            throw exception;
        }
    }
}
