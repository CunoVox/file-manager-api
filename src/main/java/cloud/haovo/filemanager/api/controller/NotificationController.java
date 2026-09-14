package cloud.haovo.filemanager.api.controller;

import cloud.haovo.filemanager.api.response.NotificationResponse;
import cloud.haovo.filemanager.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.list(page, size);
    }

    @GetMapping("/unread-count")
    public long unreadCount() {
        return service.unreadCount();
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable String id) {
        return service.markRead(id);
    }

    @PatchMapping("/read-all")
    public void markAllRead() {
        service.markAllRead();
    }
}

