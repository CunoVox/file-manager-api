package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.FileManagerService;
import cloud.haovo.filemanager.service.MinioStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class PublicFileController {
    private final FileManagerService service;

    public PublicFileController(FileManagerService service) {
        this.service = service;
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<InputStreamResource> view(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
        return stream(service.openPublic(id, range), false);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
        return stream(service.openPublic(id, range), true);
    }

    private ResponseEntity<InputStreamResource> stream(MinioStorageService.StoredContent content,
            boolean attachment) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(content.getMimeType());
        } catch (Exception ignored) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (content.getSize() != null)
            headers.setContentLength(content.getSize());
        if (content.isPartial()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + content.getRangeStart() + "-" + content.getRangeEnd() + "/" + content.getTotalSize());
        }
        if (attachment)
            headers.setContentDispositionFormData("attachment", content.getName());
        return ResponseEntity.status(content.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .headers(headers).body(new InputStreamResource(content.getStream()));
    }
}
