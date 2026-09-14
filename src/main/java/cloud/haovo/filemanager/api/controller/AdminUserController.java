package cloud.haovo.filemanager.api.controller;

import cloud.haovo.filemanager.api.request.AdminResetPasswordRequest;
import cloud.haovo.filemanager.api.request.CreateAdminUserRequest;
import cloud.haovo.filemanager.api.request.UpdateAdminUserQuotaRequest;
import cloud.haovo.filemanager.api.request.UpdateAdminUserRoleRequest;
import cloud.haovo.filemanager.api.request.UpdateAdminUserStatusRequest;
import cloud.haovo.filemanager.api.response.AdminUserResponse;
import cloud.haovo.filemanager.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/page")
    public Page<AdminUserResponse> pagedUsers(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.pageUsers(page, size);
    }

    @PostMapping
    public AdminUserResponse create(@Valid @RequestBody CreateAdminUserRequest request) {
        return service.createUser(request);
    }

    @PatchMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable String id,
            @Valid @RequestBody UpdateAdminUserRoleRequest request) {
        return service.updateRole(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminUserResponse updateStatus(@PathVariable String id,
            @RequestBody UpdateAdminUserStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @PatchMapping("/{id}/quota")
    public AdminUserResponse updateQuota(@PathVariable String id,
            @RequestBody UpdateAdminUserQuotaRequest request) {
        return service.updateQuota(id, request);
    }

    @PostMapping("/{id}/password/reset")
    public AdminUserResponse resetPassword(@PathVariable String id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        return service.resetPassword(id, request);
    }
}
