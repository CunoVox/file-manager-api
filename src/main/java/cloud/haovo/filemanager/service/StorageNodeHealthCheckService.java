package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.StorageNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StorageNodeHealthCheckService {
    private static final Logger log = LoggerFactory.getLogger(StorageNodeHealthCheckService.class);

    private final StorageNodeRepository nodes;
    private final MinioStorageService storage;
    private final NotificationService notificationService;

    public StorageNodeHealthCheckService(StorageNodeRepository nodes, MinioStorageService storage,
            NotificationService notificationService) {
        this.nodes = nodes;
        this.storage = storage;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${app.storage.health-cron:0 */10 * * * *}")
    public void checkEnabledNodes() {
        int healthy = 0;
        int failed = 0;
        for (StorageNode node : nodes.findByEnabledTrueOrderByPriorityAscIdAsc()) {
            boolean hadError = node.getLastError() != null && !node.getLastError().trim().isEmpty();
            try {
                storage.test(node);
                if (hadError) {
                    notificationService.notifyAdmins("STORAGE_NODE_RECOVERED", "Storage node recovered",
                            node.getName() + " is healthy again.", "STORAGE_NODE", String.valueOf(node.getId()));
                }
                healthy++;
            } catch (RuntimeException exception) {
                StorageNode freshNode = nodes.findById(node.getId()).orElse(node);
                boolean shouldNotify = freshNode.getLastError() == null || freshNode.getLastError().trim().isEmpty();
                freshNode.setLastError(exception.getMessage());
                nodes.save(freshNode);
                if (shouldNotify) {
                    notificationService.notifyAdmins("STORAGE_NODE_FAILED", "Storage node health check failed",
                            freshNode.getName() + " failed health check.", "STORAGE_NODE", String.valueOf(freshNode.getId()));
                }
                failed++;
                log.warn("Storage node health check failed for node {} ({})", node.getId(), node.getName());
            }
        }
        if (healthy > 0 || failed > 0) {
            log.info("Storage node health check finished: {} healthy, {} failed", healthy, failed);
        }
    }
}
