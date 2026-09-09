package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.api.FileResponse;
import cloud.haovo.filemanager.api.FileShareResponse;
import cloud.haovo.filemanager.api.FolderResponse;
import cloud.haovo.filemanager.domain.FileRecord;
import cloud.haovo.filemanager.domain.FileShare;
import cloud.haovo.filemanager.domain.Folder;
import cloud.haovo.filemanager.repository.FileRepository;
import cloud.haovo.filemanager.repository.FileShareRepository;
import cloud.haovo.filemanager.repository.FolderRepository;
import cloud.haovo.filemanager.domain.User;
import cloud.haovo.filemanager.domain.UserRole;
import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileManagerService {
    private final FileRepository fileRepository;
    private final FileShareRepository fileShareRepository;
    private final FolderRepository folderRepository;
    private final MinioStorageService storage;
    private final cloud.haovo.filemanager.repository.StorageNodeRepository storageNodes;
    private final UserRepository userRepository;
    private final UserQuotaService userQuotaService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final String publicBaseUrl;

    public FileManagerService(FileRepository fileRepository, FileShareRepository fileShareRepository, FolderRepository folderRepository,
            MinioStorageService storage, UserRepository userRepository,
            cloud.haovo.filemanager.repository.StorageNodeRepository storageNodes,
            UserQuotaService userQuotaService,
            AuditLogService auditLogService,
            NotificationService notificationService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.fileRepository = fileRepository;
        this.fileShareRepository = fileShareRepository;
        this.folderRepository = folderRepository;
        this.storage = storage;
        this.userRepository = userRepository;
        this.storageNodes = storageNodes;
        this.userQuotaService = userQuotaService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.publicBaseUrl = publicBaseUrl;
    }

    public Page<FileResponse> listFiles(String parentId, String keyword, int page, int size) {
        User user = currentUser();
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        Page<FileRecord> files = keyword == null || keyword.trim().isEmpty()
                ? (parentId == null ? fileRepository.findByOwnerIdAndParentIdIsNullAndDeletedAtIsNullOrderByNameAsc(user.getId(), pageRequest)
                        : fileRepository.findByOwnerIdAndParentIdAndDeletedAtIsNullOrderByNameAsc(user.getId(), parentId, pageRequest))
                : fileRepository.findByOwnerIdAndNameContainingIgnoreCaseAndDeletedAtIsNullOrderByNameAsc(user.getId(), keyword, pageRequest);
        return files.map(file -> FileResponse.from(file, publicBaseUrl));
    }

    public Page<FolderResponse> listFolders(String parentId, int page, int size) {
        User user = currentUser();
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        Page<Folder> folders = parentId == null
                ? folderRepository.findByOwnerIdAndParentIdIsNullAndDeletedAtIsNullOrderByNameAsc(user.getId(), pageRequest)
                : folderRepository.findByOwnerIdAndParentIdAndDeletedAtIsNullOrderByNameAsc(user.getId(), parentId, pageRequest);
        return folders.map(FolderResponse::from);
    }

    public List<FolderResponse> listAllFolders() {
        User user = currentUser();
        return folderRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(user.getId()).stream()
                .map(FolderResponse::from)
                .collect(Collectors.toList());
    }

    public Page<FileResponse> listTrashFiles(String parentId, int page, int size) {
        User user = currentUser();
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        Page<FileRecord> files = parentId == null
                ? fileRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(user.getId(), pageRequest)
                : fileRepository.findByOwnerIdAndParentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(user.getId(), parentId, pageRequest);
        return files.map(file -> FileResponse.from(file, publicBaseUrl));
    }

    public Page<FolderResponse> listTrashFolders(String parentId, int page, int size) {
        User user = currentUser();
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        Page<Folder> folders = parentId == null
                ? folderRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(user.getId(), pageRequest)
                : folderRepository.findByOwnerIdAndParentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(user.getId(), parentId, pageRequest);
        return folders.map(FolderResponse::from);
    }

    public FileResponse upload(MultipartFile upload, String parentId, Long storageNodeId) throws IOException {
        User user = currentUser();
        userQuotaService.requireAvailable(user, upload.getSize());
        StorageNode node = chooseNode(parentId, storageNodeId, upload.getSize());
        String objectKey;
        try { objectKey = storage.put(node, upload); } catch (Exception exception) { throw new IOException("MinIO upload failed", exception); }
        FileRecord file = new FileRecord();
        file.setName(upload.getOriginalFilename());
        file.setObjectKey(objectKey);
        file.setStorageNodeId(node.getId());
        file.setParentId(parentId);
        file.setOwnerId(user.getId());
        file.setMimeType(upload.getContentType() == null ? "application/octet-stream" : upload.getContentType());
        file.setSize(upload.getSize());
        try {
            FileRecord saved = fileRepository.save(file);
            auditLogService.record("FILE_UPLOADED", "FILE", saved.getId(), saved.getName(),
                    "Uploaded file " + saved.getName(),
                    Map.of("size", saved.getSize(), "storageNodeId", saved.getStorageNodeId()));
            return FileResponse.from(saved, publicBaseUrl);
        } catch (RuntimeException exception) {
            storage.delete(node, objectKey, upload.getSize());
            throw exception;
        }
    }

    public FolderResponse createFolder(String name, String parentId, Long storageNodeId) throws IOException {
        StorageNode node = chooseNode(parentId, storageNodeId, 0);
        Folder folder = new Folder();
        folder.setName(name.trim());
        folder.setParentId(parentId);
        folder.setOwnerId(currentUser().getId());
        String objectKey = storage.createFolder(node, folder.getName());
        folder.setObjectKey(objectKey);
        folder.setStorageNodeId(node.getId());
        Folder saved = folderRepository.save(folder);
        auditLogService.record("FOLDER_CREATED", "FOLDER", saved.getId(), saved.getName(),
                "Created folder " + saved.getName(), Map.of("storageNodeId", saved.getStorageNodeId()));
        return FolderResponse.from(saved);
    }

    public void deleteFile(String id) throws IOException {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        file.setDeletedAt(Instant.now());
        FileRecord saved = fileRepository.save(file);
        auditLogService.record("FILE_MOVED_TO_TRASH", "FILE", saved.getId(), saved.getName(),
                "Moved file to Trash " + saved.getName());
    }

    public FileResponse renameFile(String id, String name) {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        String oldName = file.getName();
        file.setName(cleanName(name, 255));
        FileRecord saved = fileRepository.save(file);
        auditLogService.record("FILE_RENAMED", "FILE", saved.getId(), saved.getName(),
                "Renamed file " + oldName + " to " + saved.getName(), Map.of("oldName", oldName));
        return FileResponse.from(saved, publicBaseUrl);
    }

    public FolderResponse renameFolder(String id, String name) {
        Folder folder = folderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        requireOwnerOrAdmin(folder.getOwnerId());
        String oldName = folder.getName();
        folder.setName(cleanName(name, 180));
        Folder saved = folderRepository.save(folder);
        auditLogService.record("FOLDER_RENAMED", "FOLDER", saved.getId(), saved.getName(),
                "Renamed folder " + oldName + " to " + saved.getName(), Map.of("oldName", oldName));
        return FolderResponse.from(saved);
    }

    public FileResponse moveFile(String id, String parentId) {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        String destinationId = cleanParentId(parentId);
        requireFolderDestination(destinationId, file.getOwnerId());
        file.setParentId(destinationId);
        FileRecord saved = fileRepository.save(file);
        auditLogService.record("FILE_MOVED", "FILE", saved.getId(), saved.getName(),
                "Moved file " + saved.getName(), Map.of("parentId", destinationId == null ? "root" : destinationId));
        return FileResponse.from(saved, publicBaseUrl);
    }

    public FolderResponse moveFolder(String id, String parentId) {
        Folder folder = folderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        requireOwnerOrAdmin(folder.getOwnerId());
        String destinationId = cleanParentId(parentId);
        if (folder.getId().equals(destinationId)) {
            throw new IllegalArgumentException("Cannot move a folder into itself");
        }
        if (destinationId != null) {
            Folder destination = folderRepository.findById(destinationId)
                    .orElseThrow(() -> new IllegalArgumentException("Destination folder not found"));
            if (!folder.getOwnerId().equals(destination.getOwnerId())) {
                throw new org.springframework.security.access.AccessDeniedException("You cannot move items to this folder");
            }
            if (isDescendant(destinationId, folder.getId(), folder.getOwnerId())) {
                throw new IllegalArgumentException("Cannot move a folder into one of its subfolders");
            }
        }
        folder.setParentId(destinationId);
        Folder saved = folderRepository.save(folder);
        auditLogService.record("FOLDER_MOVED", "FOLDER", saved.getId(), saved.getName(),
                "Moved folder " + saved.getName(), Map.of("parentId", destinationId == null ? "root" : destinationId));
        return FolderResponse.from(saved);
    }

    @Transactional
    public void deleteFolder(String id) throws IOException {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        requireOwnerOrAdmin(folder.getOwnerId());
        List<String> folderIds = new ArrayList<>();
        collectFolderIds(folder, folderIds);
        Instant deletedAt = Instant.now();
        for (String folderId : folderIds) {
            for (FileRecord file : fileRepository.findByOwnerIdAndParentIdOrderByNameAsc(folder.getOwnerId(), folderId)) {
                file.setDeletedAt(deletedAt);
                fileRepository.save(file);
            }
        }
        for (String folderId : folderIds) {
            Folder storedFolder = folderRepository.findById(folderId).orElse(null);
            if (storedFolder != null) {
                storedFolder.setDeletedAt(deletedAt);
                folderRepository.save(storedFolder);
            }
        }
        auditLogService.record("FOLDER_MOVED_TO_TRASH", "FOLDER", folder.getId(), folder.getName(),
                "Moved folder to Trash " + folder.getName(), Map.of("items", folderIds.size()));
    }

    public FileResponse restoreFile(String id) {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        file.setDeletedAt(null);
        FileRecord saved = fileRepository.save(file);
        auditLogService.record("FILE_RESTORED", "FILE", saved.getId(), saved.getName(),
                "Restored file " + saved.getName());
        return FileResponse.from(saved, publicBaseUrl);
    }

    public FolderResponse restoreFolder(String id) {
        Folder folder = folderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        requireOwnerOrAdmin(folder.getOwnerId());
        List<String> folderIds = new ArrayList<>();
        collectFolderIds(folder, folderIds);
        for (String folderId : folderIds) {
            for (FileRecord file : fileRepository.findByOwnerIdAndParentIdOrderByNameAsc(folder.getOwnerId(), folderId)) {
                file.setDeletedAt(null);
                fileRepository.save(file);
            }
            Folder storedFolder = folderRepository.findById(folderId).orElse(null);
            if (storedFolder != null) {
                storedFolder.setDeletedAt(null);
                folderRepository.save(storedFolder);
            }
        }
        Folder restored = folderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        auditLogService.record("FOLDER_RESTORED", "FOLDER", restored.getId(), restored.getName(),
                "Restored folder " + restored.getName(), Map.of("items", folderIds.size()));
        return FolderResponse.from(restored);
    }

    @Transactional
    public void purgeFile(String id) throws IOException {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        StorageNode fileNode = storageNodes.findById(file.getStorageNodeId()).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        storage.delete(fileNode, file.getObjectKey(), file.getSize());
        fileShareRepository.deleteByFileId(file.getId());
        fileRepository.delete(file);
        auditLogService.record("FILE_DELETED_FOREVER", "FILE", file.getId(), file.getName(),
                "Deleted file forever " + file.getName(), Map.of("size", file.getSize()));
    }

    @Transactional
    public void purgeFolder(String id) throws IOException {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        requireOwnerOrAdmin(folder.getOwnerId());
        List<String> folderIds = new ArrayList<>();
        collectFolderIds(folder, folderIds);
        for (String folderId : folderIds) {
            for (FileRecord file : fileRepository.findByOwnerIdAndParentIdOrderByNameAsc(folder.getOwnerId(), folderId)) {
                StorageNode fileNode = storageNodes.findById(file.getStorageNodeId()).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
                storage.delete(fileNode, file.getObjectKey(), file.getSize());
                fileShareRepository.deleteByFileId(file.getId());
            }
        }
        for (String folderId : folderIds) {
            Folder storedFolder = folderRepository.findById(folderId).orElse(null);
            if (storedFolder != null && storedFolder.getObjectKey() != null) {
                StorageNode folderNode = storageNodes.findById(storedFolder.getStorageNodeId()).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
                storage.delete(folderNode, storedFolder.getObjectKey(), 0);
            }
        }
        fileRepository.deleteTreeFiles(folder.getId(), folder.getOwnerId());
        folderRepository.deleteTreeFolders(folder.getId(), folder.getOwnerId());
        auditLogService.record("FOLDER_DELETED_FOREVER", "FOLDER", folder.getId(), folder.getName(),
                "Deleted folder forever " + folder.getName(), Map.of("items", folderIds.size()));
    }

    public MinioStorageService.StoredContent open(String id) throws IOException {
        return open(id, null);
    }

    public MinioStorageService.StoredContent open(String id, String rangeHeader) throws IOException {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        if (file.getDeletedAt() != null) throw new IllegalArgumentException("File not found");
        requireFileAccess(file);
        StorageNode node = storageNodes.findById(file.getStorageNodeId()).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        return openFromStorage(file, node, rangeHeader);
    }

    public MinioStorageService.StoredContent openPublic(String id) throws IOException {
        return openPublic(id, null);
    }

    public MinioStorageService.StoredContent openPublic(String id, String rangeHeader) throws IOException {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        if (file.getDeletedAt() != null || !"PUBLIC".equals(file.getVisibility())) {
            throw new org.springframework.security.access.AccessDeniedException("This file is private");
        }
        StorageNode node = storageNodes.findById(file.getStorageNodeId()).orElseThrow(() -> new IllegalArgumentException("Storage node not found"));
        return openFromStorage(file, node, rangeHeader);
    }

    private MinioStorageService.StoredContent openFromStorage(FileRecord file, StorageNode node, String rangeHeader) {
        long[] range = parseRange(rangeHeader, file.getSize());
        long expectedBytes = range == null ? file.getSize() : range[1] - range[0] + 1;
        storage.requireBandwidth(node, expectedBytes);
        if (range == null) {
            return storage.get(node, file.getObjectKey(), file.getName(), file.getMimeType(), file.getSize());
        }
        return storage.getRange(node, file.getObjectKey(), file.getName(), file.getMimeType(), file.getSize(), range[0], range[1]);
    }

    private long[] parseRange(String rangeHeader, long fileSize) {
        if (rangeHeader == null || rangeHeader.trim().isEmpty()) {
            return null;
        }
        String value = rangeHeader.trim().toLowerCase();
        if (!value.startsWith("bytes=") || value.contains(",")) {
            throw new IllegalArgumentException("Invalid range request");
        }
        String range = value.substring("bytes=".length());
        int separator = range.indexOf('-');
        if (separator < 0) {
            throw new IllegalArgumentException("Invalid range request");
        }
        String startText = range.substring(0, separator).trim();
        String endText = range.substring(separator + 1).trim();
        try {
            long start;
            long end;
            if (startText.isEmpty()) {
                long suffixLength = Long.parseLong(endText);
                if (suffixLength <= 0) throw new IllegalArgumentException("Invalid range request");
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startText);
                end = endText.isEmpty() ? fileSize - 1 : Long.parseLong(endText);
            }
            if (fileSize <= 0 || start < 0 || end < start || start >= fileSize) {
                throw new IllegalArgumentException("Invalid range request");
            }
            return new long[] { start, Math.min(end, fileSize - 1) };
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid range request");
        }
    }

    public FileResponse setVisibility(String id, String visibility) {
        FileRecord file = fileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        String normalized = visibility == null ? "" : visibility.trim().toUpperCase();
        if (!"PUBLIC".equals(normalized) && !"PRIVATE".equals(normalized)) {
            throw new IllegalArgumentException("Visibility must be PUBLIC or PRIVATE");
        }
        file.setVisibility(normalized);
        FileRecord saved = fileRepository.save(file);
        auditLogService.record("FILE_VISIBILITY_CHANGED", "FILE", saved.getId(), saved.getName(),
                "Changed file visibility to " + normalized, Map.of("visibility", normalized));
        return FileResponse.from(saved, publicBaseUrl);
    }

    public List<FileShareResponse> listFileShares(String fileId) {
        FileRecord file = fileRepository.findById(fileId).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        return fileShareRepository.findByFileIdOrderByCreatedAtDesc(fileId).stream()
                .map(share -> FileShareResponse.from(share,
                        userRepository.findById(share.getSharedWithUserId()).orElseThrow(() -> new IllegalArgumentException("User not found"))))
                .collect(Collectors.toList());
    }

    public FileShareResponse shareFile(String fileId, String email, String permission) {
        FileRecord file = fileRepository.findById(fileId).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        User target = userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new IllegalArgumentException("No user found with this email address."));
        if (file.getOwnerId().equals(target.getId())) {
            throw new IllegalArgumentException("You already own this file");
        }
        FileShare share = fileShareRepository.findByFileIdAndSharedWithUserId(fileId, target.getId())
                .orElseGet(FileShare::new);
        share.setFileId(fileId);
        share.setOwnerId(file.getOwnerId());
        share.setSharedWithUserId(target.getId());
        share.setPermission(normalizePermission(permission));
        FileShare saved = fileShareRepository.save(share);
        auditLogService.record("FILE_SHARED", "FILE", file.getId(), file.getName(),
                "Shared file " + file.getName() + " with " + target.getEmail(),
                Map.of("sharedWith", target.getEmail(), "permission", saved.getPermission()));
        User actor = currentUser();
        notificationService.notifyUser(target.getId(), "FILE_SHARED", "File shared with you",
                actor.getFullName() + " shared " + file.getName() + " with you.",
                "FILE", file.getId());
        notificationService.sendFileSharedEmail(target, actor, file.getName(), file.getId());
        return FileShareResponse.from(saved, target);
    }

    @Transactional
    public void revokeFileShare(String fileId, String userId) {
        FileRecord file = fileRepository.findById(fileId).orElseThrow(() -> new IllegalArgumentException("File not found"));
        requireOwnerOrAdmin(file.getOwnerId());
        fileShareRepository.deleteByFileIdAndSharedWithUserId(fileId, userId);
        auditLogService.record("FILE_SHARE_REVOKED", "FILE", file.getId(), file.getName(),
                "Revoked file sharing for " + file.getName(), Map.of("userId", userId));
    }

    public List<FileResponse> listSharedWithMe() {
        User user = currentUser();
        return fileShareRepository.findBySharedWithUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(share -> fileRepository.findById(share.getFileId()).orElse(null))
                .filter(file -> file != null && file.getDeletedAt() == null)
                .map(file -> FileResponse.from(file, publicBaseUrl,
                        userRepository.findById(file.getOwnerId()).orElse(null)))
                .collect(Collectors.toList());
    }

    public void writeZip(List<String> fileIds, OutputStream outputStream) throws IOException {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new IllegalArgumentException("No files selected");
        }
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (String id : fileIds) {
                MinioStorageService.StoredContent content = open(id);
                String entryName = uniqueZipEntryName(content.getName(), names);
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = content.getStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private void collectFolderIds(Folder folder, List<String> folderIds) {
        folderIds.add(folder.getId());
        for (Folder child : folderRepository.findByOwnerIdAndParentIdOrderByNameAsc(folder.getOwnerId(), folder.getId())) {
            collectFolderIds(child, folderIds);
        }
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authentication is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private void requireOwnerOrAdmin(String ownerId) {
        User user = currentUser();
        if (!user.getId().equals(ownerId) && !user.safeRoles().contains(UserRole.ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this file");
        }
    }

    private void requireFileAccess(FileRecord file) {
        User user = currentUser();
        if (user.getId().equals(file.getOwnerId()) || user.safeRoles().contains(UserRole.ADMIN)) return;
        if ("PUBLIC".equals(file.getVisibility())) return;
        if (fileShareRepository.existsByFileIdAndSharedWithUserId(file.getId(), user.getId())) return;
        throw new org.springframework.security.access.AccessDeniedException("You cannot access this file");
    }

    private StorageNode chooseNode(String parentId, Long requestedNodeId, long expectedBytes) {
        if (requestedNodeId != null) return storage.choose(requestedNodeId, expectedBytes);
        if (parentId == null || parentId.trim().isEmpty()) return storage.choose(null, expectedBytes);
        Folder folder = folderRepository.findById(parentId)
                .orElse(null);
        if (folder != null) {
            requireOwnerOrAdmin(folder.getOwnerId());
            return storage.requireCapacity(storageNodes.findById(folder.getStorageNodeId()).orElseThrow(() -> new IllegalArgumentException("Storage node not found")), expectedBytes);
        }
        return storage.choose(null, expectedBytes);
    }

    private String cleanName(String name, int maxLength) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Name is required");
        if (trimmed.length() > maxLength) throw new IllegalArgumentException("Name is too long");
        return trimmed;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) return 50;
        return Math.min(size, 100);
    }

    private String cleanParentId(String parentId) {
        return parentId == null || parentId.trim().isEmpty() ? null : parentId.trim();
    }

    private String normalizePermission(String permission) {
        String normalized = permission == null ? "VIEW" : permission.trim().toUpperCase();
        if (!"VIEW".equals(normalized) && !"DOWNLOAD".equals(normalized) && !"EDIT".equals(normalized)) {
            throw new IllegalArgumentException("Permission must be VIEW, DOWNLOAD or EDIT");
        }
        return normalized;
    }

    private void requireFolderDestination(String parentId, String ownerId) {
        if (parentId == null) return;
        Folder folder = folderRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Destination folder not found"));
        if (!folder.getOwnerId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException("You cannot move items to this folder");
        }
    }

    private boolean isDescendant(String folderId, String ancestorId, String ownerId) {
        Folder current = folderRepository.findById(folderId).orElse(null);
        while (current != null && current.getParentId() != null) {
            if (!ownerId.equals(current.getOwnerId())) return false;
            if (current.getParentId().equals(ancestorId)) return true;
            current = folderRepository.findById(current.getParentId()).orElse(null);
        }
        return false;
    }

    private String uniqueZipEntryName(String name, Set<String> usedNames) {
        String cleaned = name == null || name.trim().isEmpty() ? "file" : name.trim();
        cleaned = cleaned.replace("\\", "_").replace("/", "_");
        if (usedNames.add(cleaned)) return cleaned;

        int dot = cleaned.lastIndexOf('.');
        String base = dot > 0 ? cleaned.substring(0, dot) : cleaned;
        String extension = dot > 0 ? cleaned.substring(dot) : "";
        int index = 2;
        String candidate;
        do {
            candidate = base + " (" + index + ")" + extension;
            index++;
        } while (!usedNames.add(candidate));
        return candidate;
    }
}
