package cloud.haovo.filemanager.api;

import lombok.Value;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@Value
public class ApiErrorResponse {
    Instant timestamp;
    int status;
    String error;
    String code;
    String message;
    String path;
    Map<String, String> details;

    public static ApiErrorResponse of(HttpStatus status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path, null);
    }

    public static ApiErrorResponse of(HttpStatus status, String code, String message, String path,
            Map<String, String> details) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path, details);
    }
}
