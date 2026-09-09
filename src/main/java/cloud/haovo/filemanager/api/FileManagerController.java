package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.FileManagerService;
import cloud.haovo.filemanager.service.MinioStorageService;
import org.springframework.data.domain.Page;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FileManagerController {
    private final FileManagerService service;

    public FileManagerController(FileManagerService service) {
        this.service = service;
    }

    @GetMapping("/files")
    public Page<FileResponse> files(@RequestParam(required = false) String parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listFiles(parentId, keyword, page, size);
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileResponse upload(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) Long storageNodeId) throws IOException {
        return service.upload(file, parentId, storageNodeId);
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) throws IOException {
        service.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/files/{id}/restore")
    public FileResponse restoreFile(@PathVariable String id) {
        return service.restoreFile(id);
    }

    @DeleteMapping("/files/{id}/forever")
    public ResponseEntity<Void> purgeFile(@PathVariable String id) throws IOException {
        service.purgeFile(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/files/{id}")
    public FileResponse renameFile(@PathVariable String id, @Valid @RequestBody RenameRequest request) {
        return service.renameFile(id, request.getName());
    }

    @PatchMapping("/files/{id}/move")
    public FileResponse moveFile(@PathVariable String id, @RequestBody MoveRequest request) {
        return service.moveFile(id, request.getParentId());
    }

    @PatchMapping("/files/{id}/visibility")
    public FileResponse setVisibility(@PathVariable String id, @RequestBody VisibilityRequest request) {
        return service.setVisibility(id, request.getVisibility());
    }

    @GetMapping("/files/{id}/shares")
    public List<FileShareResponse> fileShares(@PathVariable String id) {
        return service.listFileShares(id);
    }

    @PostMapping("/files/{id}/shares")
    public FileShareResponse shareFile(@PathVariable String id, @RequestBody ShareRequest request) {
        return service.shareFile(id, request.getEmail(), request.getPermission());
    }

    @DeleteMapping("/files/{fileId}/shares/{userId}")
    public ResponseEntity<Void> revokeFileShare(@PathVariable String fileId, @PathVariable String userId) {
        service.revokeFileShare(fileId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/folders")
    public Page<FolderResponse> folders(@RequestParam(required = false) String parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listFolders(parentId, page, size);
    }

    @GetMapping("/folders/all")
    public List<FolderResponse> allFolders() {
        return service.listAllFolders();
    }

    @PostMapping("/folders")
    public FolderResponse createFolder(@Valid @RequestBody CreateFolderRequest request,
            @RequestParam(required = false) Long storageNodeId) throws IOException {
        return service.createFolder(request.getName(), request.getParentId(), storageNodeId);
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) throws IOException {
        service.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folders/{id}/restore")
    public FolderResponse restoreFolder(@PathVariable String id) {
        return service.restoreFolder(id);
    }

    @DeleteMapping("/folders/{id}/forever")
    public ResponseEntity<Void> purgeFolder(@PathVariable String id) throws IOException {
        service.purgeFolder(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/folders/{id}")
    public FolderResponse renameFolder(@PathVariable String id, @Valid @RequestBody RenameRequest request) {
        return service.renameFolder(id, request.getName());
    }

    @PatchMapping("/folders/{id}/move")
    public FolderResponse moveFolder(@PathVariable String id, @RequestBody MoveRequest request) {
        return service.moveFolder(id, request.getParentId());
    }

    @GetMapping("/files/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
        return stream(service.open(id, range), false);
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<InputStreamResource> view(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
        return stream(service.open(id, range), false);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
        return stream(service.open(id, range), true);
    }

    @PostMapping("/files/download-zip")
    public ResponseEntity<StreamingResponseBody> downloadZip(@RequestBody BulkDownloadRequest request) {
        StreamingResponseBody body = outputStream -> service.writeZip(request.getFileIds(), outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"haobox-files.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @GetMapping("/trash/files")
    public Page<FileResponse> trashFiles(@RequestParam(required = false) String parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listTrashFiles(parentId, page, size);
    }

    @GetMapping("/trash/folders")
    public Page<FolderResponse> trashFolders(@RequestParam(required = false) String parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listTrashFolders(parentId, page, size);
    }

    @GetMapping("/shared/files")
    public List<FileResponse> sharedFiles() {
        return service.listSharedWithMe();
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
