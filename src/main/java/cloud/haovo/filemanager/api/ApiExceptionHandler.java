package cloud.haovo.filemanager.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please check the highlighted fields and try again.", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please check the request and try again.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "The request body is invalid. Please check the submitted data.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", friendlyBadRequestMessage(exception.getMessage()), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "REQUEST_CONFLICT", friendlyConflictMessage(exception.getMessage()), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception,
            HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", friendlyAccessDeniedMessage(exception.getMessage()), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception,
            HttpServletRequest request) {
        String message = exception instanceof DisabledException
                ? "This account has been locked. Please contact an administrator."
                : "Invalid email or password.";
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "This file is too large to upload.", request);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiErrorResponse> handleIo(IOException exception, HttpServletRequest request) {
        log.warn("Storage operation failed", exception);
        return error(HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE",
                "Storage is temporarily unavailable. Please try again later.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected API error", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Please try again later.", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, code, message, request.getRequestURI()));
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request, Map<String, String> details) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, code, message, request.getRequestURI(), details));
    }

    private String friendlyBadRequestMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Please check the request and try again.";
        }
        if ("User is disabled".equals(message)) {
            return "This account has been locked. Please contact an administrator.";
        }
        return message;
    }

    private String friendlyAccessDeniedMessage(String message) {
        if (message == null || message.trim().isEmpty() || "Access denied".equalsIgnoreCase(message)) {
            return "You do not have permission to perform this action.";
        }
        return message;
    }

    private String friendlyConflictMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "The request could not be completed right now.";
        }
        if ("USER_STORAGE_QUOTA_EXCEEDED".equals(message)) {
            return "Your storage is full. Delete files permanently or contact an administrator to increase your quota.";
        }
        if ("OTP_EMAIL_FAILED".equals(message)) {
            return "Could not send the verification code. Please contact an administrator.";
        }
        if ("SMTP is disabled".equals(message)) {
            return "Enable SMTP before sending a test email.";
        }
        if ("SMTP test failed".equals(message)) {
            return "Could not send the test email. Please check the SMTP settings.";
        }
        if (message.contains("hash refresh token")) {
            return "Your session could not be updated. Please sign in again.";
        }
        if (message.startsWith("S3 test failed")) {
            return "Could not connect to this storage node. Check endpoint, bucket, access key, and secret key.";
        }
        if (message.contains("storage node") || message.contains("Storage node")) {
            return message;
        }
        return "The request could not be completed right now.";
    }
}
