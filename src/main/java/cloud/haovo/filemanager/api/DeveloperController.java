package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.DeveloperApiService;
import cloud.haovo.filemanager.service.FileManagerService;
import cloud.haovo.filemanager.service.MinioStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/developer")
public class DeveloperController {
    private final DeveloperApiService developerApiService;
    private final FileManagerService fileManagerService;

    public DeveloperController(DeveloperApiService developerApiService, FileManagerService fileManagerService) {
        this.developerApiService = developerApiService;
        this.fileManagerService = fileManagerService;
    }

    @GetMapping("/api-keys")
    public List<DeveloperApiKeyResponse> apiKeys() {
        return developerApiService.listKeys();
    }

    @PostMapping("/api-keys")
    public DeveloperApiKeyResponse createApiKey(@Valid @RequestBody DeveloperApiKeyRequest request) {
        return developerApiService.createKey(request);
    }

    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable String id) {
        developerApiService.revokeKey(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usage")
    public Page<ApiUsageLogResponse> usage(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return developerApiService.usageLogs(page, size);
    }

    @GetMapping("/stats")
    public DeveloperStatsResponse stats() {
        return developerApiService.stats();
    }

    @GetMapping("/files")
    public Page<FileResponse> files(@RequestParam(required = false) String parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        developerApiService.requireScope("files:read");
        return fileManagerService.listFiles(parentId, keyword, page, size);
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileResponse upload(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) Long storageNodeId) throws IOException {
        developerApiService.requireScope("files:write");
        return fileManagerService.upload(file, parentId, storageNodeId);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
        developerApiService.requireScope("files:read");
        return stream(fileManagerService.open(id, range), true);
    }

    @PatchMapping("/files/{id}/rename")
    public FileResponse renameFile(@PathVariable String id, @Valid @RequestBody RenameRequest request) {
        developerApiService.requireScope("files:write");
        return fileManagerService.renameFile(id, request.getName());
    }

    @PatchMapping("/files/{id}/move")
    public FileResponse moveFile(@PathVariable String id, @RequestBody MoveRequest request) {
        developerApiService.requireScope("files:write");
        return fileManagerService.moveFile(id, request.getParentId());
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable String id) throws IOException {
        developerApiService.requireScope("files:delete");
        fileManagerService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/folders")
    public Page<FolderResponse> folders(@RequestParam(required = false) String parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        developerApiService.requireScope("folders:read");
        return fileManagerService.listFolders(parentId, page, size);
    }

    @PostMapping("/folders")
    public FolderResponse createFolder(@Valid @RequestBody CreateFolderRequest request,
            @RequestParam(required = false) Long storageNodeId) throws IOException {
        developerApiService.requireScope("folders:write");
        return fileManagerService.createFolder(request.getName(), request.getParentId(), storageNodeId);
    }

    @PatchMapping("/folders/{id}/rename")
    public FolderResponse renameFolder(@PathVariable String id, @Valid @RequestBody RenameRequest request) {
        developerApiService.requireScope("folders:write");
        return fileManagerService.renameFolder(id, request.getName());
    }

    @PatchMapping("/folders/{id}/move")
    public FolderResponse moveFolder(@PathVariable String id, @RequestBody MoveRequest request) {
        developerApiService.requireScope("folders:write");
        return fileManagerService.moveFolder(id, request.getParentId());
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) throws IOException {
        developerApiService.requireScope("folders:write");
        fileManagerService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<InputStreamResource> stream(MinioStorageService.StoredContent content,
            boolean attachment) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.getMimeType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (content.getSize() != null) headers.setContentLength(content.getSize());
        if (content.isPartial()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + content.getRangeStart() + "-" + content.getRangeEnd() + "/" + content.getTotalSize());
        }
        if (attachment) headers.setContentDispositionFormData("attachment", content.getName());
        return ResponseEntity.status(content.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .headers(headers).body(new InputStreamResource(content.getStream()));
    }
}
