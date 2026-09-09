package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.*;
import cloud.haovo.filemanager.domain.StorageMigrationJob;
import cloud.haovo.filemanager.domain.StorageMigrationStatus;
import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {
    private final UserRepository users;
    private final FileRepository files;
    private final StorageNodeRepository nodes;
    private final StorageMigrationJobRepository migrations;
    private final AuditLogRepository auditLogs;
    private final UserQuotaService quotaService;

    public AdminDashboardService(UserRepository users, FileRepository files, StorageNodeRepository nodes,
            StorageMigrationJobRepository migrations, AuditLogRepository auditLogs, UserQuotaService quotaService) {
        this.users = users;
        this.files = files;
        this.nodes = nodes;
        this.migrations = migrations;
        this.auditLogs = auditLogs;
        this.quotaService = quotaService;
    }

    public AdminDashboardResponse getDashboard() {
        List<User> allUsers = users.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<StorageNode> allNodes = nodes.findAll(Sort.by(Sort.Direction.ASC, "priority").and(Sort.by("id")));
        List<StorageMigrationJob> recentMigrations = migrations.findTop20ByOrderByCreatedAtDesc();

        long storageCapacity = allNodes.stream().mapToLong(StorageNode::getCapacityBytes).sum();
        long storageUsed = allNodes.stream().mapToLong(StorageNode::getUsedBytes).sum();
        long bandwidthLimit = allNodes.stream().mapToLong(StorageNode::getBandwidthLimitBytes).sum();
        long bandwidthUsed = allNodes.stream().mapToLong(StorageNode::getBandwidthUsedBytes).sum();
        long healthyNodes = allNodes.stream()
                .filter(node -> node.isEnabled() && (node.getLastError() == null || node.getLastError().trim().isEmpty()))
                .count();

        List<AdminUserResponse> topUsers = allUsers.stream()
                .map(user -> AdminUserResponse.from(user, quotaService.effectiveQuotaBytes(user),
                        quotaService.usedBytes(user)))
                .sorted(Comparator.comparingLong(AdminUserResponse::getStorageUsedBytes).reversed())
                .limit(8)
                .collect(Collectors.toList());

        List<AdminDashboardResponse.Alert> alerts = buildAlerts(allNodes, topUsers, recentMigrations);

        AdminDashboardResponse.Totals totals = new AdminDashboardResponse.Totals(
                allUsers.size(),
                users.countByEnabledTrue(),
                files.count(),
                storageUsed,
                storageCapacity,
                bandwidthUsed,
                bandwidthLimit,
                allNodes.size(),
                healthyNodes,
                alerts.size());

        return new AdminDashboardResponse(totals,
                allNodes.stream().map(StorageNodeResponse::from).collect(Collectors.toList()),
                topUsers,
                recentMigrations.stream().limit(8).map(this::migrationResponse).collect(Collectors.toList()),
                auditLogs.findTop10ByOrderByCreatedAtDesc().stream().map(AuditLogResponse::from).collect(Collectors.toList()),
                alerts);
    }

    private List<AdminDashboardResponse.Alert> buildAlerts(List<StorageNode> nodes,
            List<AdminUserResponse> topUsers, List<StorageMigrationJob> migrations) {
        List<AdminDashboardResponse.Alert> alerts = new ArrayList<>();
        for (StorageNode node : nodes) {
            if (node.getLastError() != null && !node.getLastError().trim().isEmpty()) {
                alerts.add(alert("STORAGE_NODE_FAILED", "danger", node.getName() + " has a health check error",
                        node.getLastError(), "STORAGE_NODE", String.valueOf(node.getId())));
            }
            if (percent(node.getUsedBytes(), node.getCapacityBytes()) >= 90) {
                alerts.add(alert("STORAGE_NODE_NEAR_CAPACITY", "warning", node.getName() + " is near capacity",
                        "Storage usage is " + Math.round(percent(node.getUsedBytes(), node.getCapacityBytes())) + "%.",
                        "STORAGE_NODE", String.valueOf(node.getId())));
            }
            if (percent(node.getBandwidthUsedBytes(), node.getBandwidthLimitBytes()) >= 90) {
                alerts.add(alert("STORAGE_NODE_NEAR_BANDWIDTH", "warning", node.getName() + " is near bandwidth limit",
                        "Bandwidth usage is " + Math.round(percent(node.getBandwidthUsedBytes(), node.getBandwidthLimitBytes())) + "%.",
                        "STORAGE_NODE", String.valueOf(node.getId())));
            }
        }
        for (AdminUserResponse user : topUsers) {
            Long quota = user.getEffectiveStorageQuotaBytes();
            if (quota != null && percent(user.getStorageUsedBytes(), quota) >= 90) {
                alerts.add(alert("USER_NEAR_QUOTA", "warning", user.getEmail() + " is near storage quota",
                        "User storage usage is " + Math.round(percent(user.getStorageUsedBytes(), quota)) + "%.",
                        "USER", user.getId()));
            }
        }
        migrations.stream()
                .filter(job -> job.getStatus() == StorageMigrationStatus.FAILED)
                .limit(3)
                .forEach(job -> alerts.add(alert("STORAGE_MIGRATION_FAILED", "danger",
                        "Migration failed", job.getErrorMessage(), "STORAGE_MIGRATION", job.getId())));
        return alerts.stream().limit(12).collect(Collectors.toList());
    }

    private AdminDashboardResponse.Alert alert(String type, String severity, String title, String message,
            String targetType, String targetId) {
        return new AdminDashboardResponse.Alert(type, severity, title,
                message == null || message.trim().isEmpty() ? "Needs attention." : message,
                targetType, targetId);
    }

    private StorageMigrationJobResponse migrationResponse(StorageMigrationJob job) {
        String sourceName = nodes.findById(job.getSourceNodeId()).map(StorageNode::getName).orElse("Deleted node");
        String targetName = nodes.findById(job.getTargetNodeId()).map(StorageNode::getName).orElse("Deleted node");
        return StorageMigrationJobResponse.from(job, sourceName, targetName);
    }

    private double percent(long value, long limit) {
        if (limit <= 0) return 0;
        return (double) value * 100D / (double) limit;
    }
}
