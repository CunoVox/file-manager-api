package cloud.haovo.filemanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var mapping = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Accept-Ranges", "Content-Range", "Content-Length", "Content-Disposition");
        if (hasText(allowedOrigins)) {
            mapping.allowedOrigins(split(allowedOrigins));
        }
        if (hasText(allowedOriginPatterns)) {
            mapping.allowedOriginPatterns(split(allowedOriginPatterns));
        }
    }

    private String[] split(String value) {
        return value.split("\\s*,\\s*");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
