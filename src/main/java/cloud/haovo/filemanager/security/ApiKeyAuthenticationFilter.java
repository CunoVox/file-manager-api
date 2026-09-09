package cloud.haovo.filemanager.security;

import cloud.haovo.filemanager.api.ApiErrorResponse;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.service.DeveloperApiService;
import cloud.haovo.filemanager.service.DeveloperApiService.RateLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final DeveloperApiService developerApiService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(DeveloperApiService developerApiService, ObjectMapper objectMapper) {
        this.developerApiService = developerApiService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/developer/")) {
            chain.doFilter(request, response);
            return;
        }
        long started = System.currentTimeMillis();
        String errorMessage = null;
        try {
            String token = bearerToken(request);
            if (token != null && token.startsWith("hb_live_")) {
                DeveloperApiService.AuthenticatedApiKey authenticated = developerApiService.authenticate(token);
                User user = authenticated.getUser();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        user.safeRoles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                                .collect(Collectors.toList()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(DeveloperApiService.REQUEST_API_KEY, authenticated.getKey());
                request.setAttribute(DeveloperApiService.REQUEST_API_USER, user);
            }
            chain.doFilter(request, response);
        } catch (RateLimitExceededException exception) {
            errorMessage = "Rate limit exceeded";
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many API requests. Please try again later.", request.getRequestURI());
        } catch (AccessDeniedException exception) {
            errorMessage = exception.getMessage();
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY",
                    "The API key is invalid, expired, or no longer active.", request.getRequestURI());
        } finally {
            developerApiService.recordUsage(request, response.getStatus(),
                    System.currentTimeMillis() - started, errorMessage);
        }
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7).trim();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(status, code, message, path));
    }
}
