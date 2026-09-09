package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.AuditLogResponse;
import cloud.haovo.filemanager.domain.AuditLog;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.repository.AuditLogRepository;
import cloud.haovo.filemanager.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditLogService {
    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditLogService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    public Page<AuditLogResponse> search(String action, String keyword, Instant from, Instant to, int page, int size) {
        String cleanAction = clean(action);
        String cleanKeyword = clean(keyword);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        return logs.search(cleanAction, cleanKeyword,
                from, to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(AuditLogResponse::from);
    }

    public void record(String action, String targetType, String targetId, String targetName, String message) {
        record(action, targetType, targetId, targetName, message, null);
    }

    public void record(String action, String targetType, String targetId, String targetName, String message,
            Map<String, ?> metadata) {
        AuditLog log = new AuditLog();
        User actor = currentActor();
        if (actor != null) {
            log.setActorUserId(actor.getId());
            log.setActorEmail(actor.getEmail());
        }
        HttpServletRequest request = currentRequest();
        if (request != null) {
            log.setIpAddress(clientIp(request));
            log.setUserAgent(trim(request.getHeader("User-Agent"), 500));
        }
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetName(trim(targetName, 255));
        log.setMessage(trim(message, 500));
        log.setMetadataJson(toJson(metadata));
        logs.save(log);
    }

    public void system(String action, String targetType, String targetId, String targetName, String message,
            Map<String, ?> metadata) {
        AuditLog log = new AuditLog();
        log.setActorEmail("system");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetName(trim(targetName, 255));
        log.setMessage(trim(message, 500));
        log.setMetadataJson(toJson(metadata));
        logs.save(log);
    }

    private User currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return trim(forwarded.split(",")[0].trim(), 80);
        }
        return trim(request.getRemoteAddr(), 80);
    }

    private String clean(String value) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        return metadata.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trim(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
