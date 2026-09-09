package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

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
