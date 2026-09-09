package cloud.haovo.filemanager.api;

import lombok.Value;

import java.util.List;

@Value
public class AdminDashboardResponse {
    Totals totals;
    List<StorageNodeResponse> nodes;
    List<AdminUserResponse> topUsers;
    List<StorageMigrationJobResponse> recentMigrations;
    List<AuditLogResponse> recentAuditLogs;
    List<Alert> alerts;

    @Value
    public static class Totals {
        long users;
        long activeUsers;
        long files;
        long storageUsedBytes;
        long storageCapacityBytes;
        long bandwidthUsedBytes;
        long bandwidthLimitBytes;
        long storageNodes;
        long healthyNodes;
        long unreadAlerts;
    }

    @Value
    public static class Alert {
        String type;
        String severity;
        String title;
        String message;
        String targetType;
        String targetId;
    }
}
