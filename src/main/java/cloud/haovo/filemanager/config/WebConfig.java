package cloud.haovo.filemanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "Accept-Ranges",
                "Content-Range",
                "Content-Length",
                "Content-Disposition"));
        applyOrigins(configuration);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var mapping = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Accept-Ranges", "Content-Range", "Content-Length", "Content-Disposition");
        if (hasText(allowedOrigins)) {
            mapping.allowedOrigins(split(allowedOrigins));
        }
        if (hasText(allowedOriginPatterns)) {
            mapping.allowedOriginPatterns(split(allowedOriginPatterns));
        }
    }

    private void applyOrigins(CorsConfiguration configuration) {
        if (hasText(allowedOrigins)) {
            configuration.setAllowedOrigins(Arrays.asList(split(allowedOrigins)));
        }
        if (hasText(allowedOriginPatterns)) {
            configuration.setAllowedOriginPatterns(Arrays.asList(split(allowedOriginPatterns)));
        }
    }

    private String[] split(String value) {
        return value.split("\\s*,\\s*");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
