package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.domain.ApiUsageLog;
import lombok.Value;

import java.time.Instant;

@Value
public class ApiUsageLogResponse {
    String id;
    String apiKeyId;
    String apiKeyName;
    String method;
    String path;
    int status;
    long durationMs;
    String ipAddress;
    String errorMessage;
    Instant createdAt;

    public static ApiUsageLogResponse from(ApiUsageLog log) {
        return new ApiUsageLogResponse(log.getId(), log.getApiKeyId(), log.getApiKeyName(), log.getMethod(),
                log.getPath(), log.getStatus(), log.getDurationMs(), log.getIpAddress(),
                log.getErrorMessage(), log.getCreatedAt());
    }
}
