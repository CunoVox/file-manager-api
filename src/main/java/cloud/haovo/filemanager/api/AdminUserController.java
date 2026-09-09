package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.AdminUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserResponse> users() {
        return service.listUsers();
    }

    @PostMapping
    public AdminUserResponse create(@Valid @RequestBody AdminUserRequests.Create request) {
        return service.createUser(request);
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable String id,
            @Valid @RequestBody AdminUserRequests.UpdateRole request) {
        return service.updateRole(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminUserResponse updateStatus(@PathVariable String id,
            @RequestBody AdminUserRequests.UpdateStatus request) {
        return service.updateStatus(id, request);
    }

    @PatchMapping("/{id}/quota")
    public AdminUserResponse updateQuota(@PathVariable String id,
            @RequestBody AdminUserRequests.UpdateQuota request) {
        return service.updateQuota(id, request);
    }

    @PostMapping("/{id}/password/reset")
    public AdminUserResponse resetPassword(@PathVariable String id,
            @Valid @RequestBody AdminUserRequests.ResetPassword request) {
        return service.resetPassword(id, request);
    }
}
