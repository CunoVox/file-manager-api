package cloud.haovo.filemanager.api.response;

import lombok.Value;

@Value
public class DeveloperStatsResponse {
    long apiKeys;
    long activeApiKeys;
    long requestsToday;
    long failedRequestsToday;
    int rateLimitPerMinute;
}


