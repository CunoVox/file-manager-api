package cloud.haovo.filemanager.api;

import lombok.Value;

@Value
public class DeveloperStatsResponse {
    long apiKeys;
    long activeApiKeys;
    long requestsToday;
    long failedRequestsToday;
    int rateLimitPerMinute;
}
