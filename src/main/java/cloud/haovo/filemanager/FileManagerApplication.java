package cloud.haovo.filemanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class FileManagerApplication {
    private static final Logger log = LoggerFactory.getLogger(FileManagerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(FileManagerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printApiDocsLinks(ApplicationReadyEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath == null || "/".equals(contextPath)) {
            contextPath = "";
        }
        log.info("Swagger UI: http://localhost:{}{}/swagger-ui/index.html", port, contextPath);
        log.info("OpenAPI JSON: http://localhost:{}{}/v3/api-docs", port, contextPath);
    }
}
