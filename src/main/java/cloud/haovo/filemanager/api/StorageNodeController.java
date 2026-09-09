package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.StorageNodeRepository;
import cloud.haovo.filemanager.service.AuditLogService;
import cloud.haovo.filemanager.service.MinioStorageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/storage-nodes")
@PreAuthorize("hasRole('ADMIN')")
public class StorageNodeController {
    private final StorageNodeRepository repository;
    private final MinioStorageService storage;
    private final AuditLogService auditLogService;

    public StorageNodeController(StorageNodeRepository repository, MinioStorageService storage,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.storage = storage;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<StorageNodeResponse> list() { return repository.findAll().stream().map(StorageNodeResponse::from).collect(Collectors.toList()); }

    @PostMapping
    public StorageNodeResponse create(@Valid @RequestBody StorageNodeRequest request) {
        requireCredential(request);
        StorageNode node = new StorageNode();
        apply(node, request);
        node.setBandwidthResetAt(Instant.now());
        StorageNode saved = repository.save(node);
        auditLogService.record("STORAGE_NODE_CREATED", "STORAGE_NODE", String.valueOf(saved.getId()), saved.getName(),
                "Created storage node " + saved.getName(), Map.of("endpoint", saved.getEndpoint(), "bucket", saved.getBucket()));
        return StorageNodeResponse.from(saved);
    }

    @PatchMapping("/{id}")
    public StorageNodeResponse update(@PathVariable Long id, @Valid @RequestBody StorageNodeRequest request) {
        StorageNode node = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        apply(node, request);
        StorageNode saved = repository.save(node);
        auditLogService.record("STORAGE_NODE_UPDATED", "STORAGE_NODE", String.valueOf(saved.getId()), saved.getName(),
                "Updated storage node " + saved.getName());
        return StorageNodeResponse.from(saved);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        StorageNode node = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        if (node.getUsedBytes() > 0) throw new IllegalStateException("Disable this storage node first. It still contains files.");
        repository.delete(node);
        auditLogService.record("STORAGE_NODE_DELETED", "STORAGE_NODE", String.valueOf(node.getId()), node.getName(),
                "Deleted storage node " + node.getName());
    }

    @PostMapping("/{id}/test")
    public StorageNodeResponse test(@PathVariable Long id) {
        StorageNode node = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        try { storage.test(node); } catch (RuntimeException exception) { node.setLastError(exception.getMessage()); repository.save(node); throw exception; }
        auditLogService.record("STORAGE_NODE_TESTED", "STORAGE_NODE", String.valueOf(node.getId()), node.getName(),
                "Tested storage node " + node.getName());
        return StorageNodeResponse.from(node);
    }

    @PostMapping("/{id}/bandwidth/reset")
    public StorageNodeResponse resetBandwidth(@PathVariable Long id) {
        StorageNode node = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        node.setBandwidthUsedBytes(0);
        node.setBandwidthResetAt(Instant.now());
        StorageNode saved = repository.save(node);
        auditLogService.record("STORAGE_BANDWIDTH_RESET", "STORAGE_NODE", String.valueOf(saved.getId()), saved.getName(),
                "Reset bandwidth for storage node " + saved.getName());
        return StorageNodeResponse.from(saved);
    }

    @PostMapping("/{id}/enabled")
    public StorageNodeResponse setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        StorageNode node = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        node.setEnabled(enabled);
        StorageNode saved = repository.save(node);
        auditLogService.record(enabled ? "STORAGE_NODE_ENABLED" : "STORAGE_NODE_DISABLED",
                "STORAGE_NODE", String.valueOf(saved.getId()), saved.getName(),
                (enabled ? "Enabled storage node " : "Disabled storage node ") + saved.getName());
        return StorageNodeResponse.from(saved);
    }

    private void apply(StorageNode node, StorageNodeRequest request) {
        node.setName(request.getName().trim()); node.setNote(trimToNull(request.getNote())); node.setEndpoint(request.getEndpoint().trim()); node.setRegion(request.getRegion()); if (hasText(request.getAccessKey())) node.setAccessKey(request.getAccessKey()); if (hasText(request.getSecretKey())) node.setSecretKey(request.getSecretKey()); node.setBucket(request.getBucket().trim()); node.setCapacityBytes(request.getCapacityBytes()); node.setBandwidthLimitBytes(request.getBandwidthLimitBytes()); node.setPriority(request.getPriority()); node.setEnabled(request.isEnabled());
    }

    private void requireCredential(StorageNodeRequest request) {
        if (!hasText(request.getAccessKey()) || !hasText(request.getSecretKey())) {
            throw new IllegalArgumentException("Access key and secret key are required");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
