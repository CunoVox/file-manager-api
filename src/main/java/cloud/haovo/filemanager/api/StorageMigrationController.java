package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.StorageMigrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/storage-migrations")
@PreAuthorize("hasRole('ADMIN')")
public class StorageMigrationController {
    private final StorageMigrationService service;

    public StorageMigrationController(StorageMigrationService service) {
        this.service = service;
    }

    @GetMapping
    public List<StorageMigrationJobResponse> jobs() {
        return service.listJobs();
    }

    @PostMapping("/source/{sourceNodeId}")
    public StorageMigrationJobResponse start(@PathVariable Long sourceNodeId,
            @Valid @RequestBody StorageMigrationRequest request) {
        return service.startMigration(sourceNodeId, request.getTargetNodeId());
    }
}
