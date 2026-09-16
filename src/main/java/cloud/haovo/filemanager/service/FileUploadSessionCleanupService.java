package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.FileUploadSession;
import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.FileUploadSessionRepository;
import cloud.haovo.filemanager.repository.StorageNodeRepository;
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
    private final StorageNodeRepository storageNodes;
    private final MinioStorageService storage;
    private final Duration timeout;

    public FileUploadSessionCleanupService(FileUploadSessionRepository uploadSessionRepository,
            StorageNodeRepository storageNodes,
            MinioStorageService storage,
            @Value("${app.upload.session-timeout-hours:24}") long timeoutHours) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.storageNodes = storageNodes;
        this.storage = storage;
        this.timeout = Duration.ofHours(timeoutHours);
    }

    @Transactional
    @Scheduled(cron = "${app.upload.cleanup-cron:0 */30 * * * *}")
    public void cleanupStaleUploads() {
        Instant cutoff = Instant.now().minus(timeout);
        int cleaned = 0;
        try {
            for (FileUploadSession session : uploadSessionRepository.findByStatusAndUpdatedAtBefore("UPLOADING", cutoff)) {
                abortDirectMultipartUpload(session);
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

    private void abortDirectMultipartUpload(FileUploadSession session) {
        if (!"DIRECT_MULTIPART".equals(session.getUploadMode())
                || session.getObjectKey() == null
                || session.getMultipartUploadId() == null) {
            return;
        }
        try {
            StorageNode node = storageNodes.findById(session.getStorageNodeId()).orElse(null);
            if (node != null) {
                storage.abortMultipartUpload(node, session.getObjectKey(), session.getMultipartUploadId());
            }
        } catch (Exception exception) {
            log.warn("Could not abort stale multipart upload {}", session.getId(), exception);
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
