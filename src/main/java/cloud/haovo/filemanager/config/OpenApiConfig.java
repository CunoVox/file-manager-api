package cloud.haovo.filemanager.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI fileManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HaoBox File Manager API")
                        .version("v1")
                        .description("API documentation for HaoBox file management and MinIO/S3 storage."));
    }
}
