package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.AdminDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public AdminDashboardResponse dashboard() {
        return service.getDashboard();
    }
}
