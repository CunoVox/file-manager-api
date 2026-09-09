package cloud.haovo.filemanager.service;

import cloud.haovo.filemanager.domain.StorageNode;
import cloud.haovo.filemanager.repository.StorageNodeRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
public class MinioStorageService {
    private static final long MIN_FREE_BYTES = 500L * 1024L * 1024L;
    private static final double SOFT_LIMIT_RATIO = 0.95D;

    private final StorageNodeRepository nodes;

    public MinioStorageService(StorageNodeRepository nodes) { this.nodes = nodes; }

    public StorageNode choose(Long requestedNodeId) {
        return choose(requestedNodeId, 0);
    }

    public StorageNode choose(Long requestedNodeId, long expectedBytes) {
        if (requestedNodeId != null) {
            return requireCapacity(nodes.findById(requestedNodeId).filter(StorageNode::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Storage node not found or disabled")), expectedBytes);
        }
        return nodes.findUploadCandidates().stream()
                .filter(node -> hasCapacity(node, expectedBytes))
                .filter(node -> hasBandwidth(node, expectedBytes))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No storage node has enough free capacity or bandwidth"));
    }

    public StorageNode requireCapacity(StorageNode node, long expectedBytes) {
        if (!node.isEnabled()) {
            throw new IllegalArgumentException("Storage node is disabled");
        }
        if (!hasCapacity(node, expectedBytes)) {
            throw new IllegalStateException("Storage node does not have enough free capacity");
        }
        if (!hasBandwidth(node, expectedBytes)) {
            throw new IllegalStateException("Storage node does not have enough bandwidth");
        }
        return node;
    }

    public StorageNode requireBandwidth(StorageNode node, long expectedBytes) {
        if (!hasBandwidth(node, expectedBytes)) {
            throw new IllegalStateException("Storage node does not have enough bandwidth");
        }
        return node;
    }

    public String put(StorageNode node, MultipartFile file) throws Exception {
        String key = Instant.now().toString().substring(0, 10) + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        try (InputStream stream = file.getInputStream()) {
            client(node).putObject(PutObjectRequest.builder().bucket(node.getBucket()).key(key).contentType(contentType(file)).build(), RequestBody.fromInputStream(stream, file.getSize()));
        }
        node.setUsedBytes(node.getUsedBytes() + file.getSize());
        node.setBandwidthUsedBytes(node.getBandwidthUsedBytes() + file.getSize());
        nodes.save(node);
        return key;
    }

    public String createFolder(StorageNode node, String name) {
        String key = "folders/" + UUID.randomUUID() + "/" + name.trim() + "/";
        client(node).putObject(PutObjectRequest.builder().bucket(node.getBucket()).key(key).contentType("application/x-directory").build(), RequestBody.empty());
        return key;
    }

    public String copyToNode(StorageNode source, StorageNode target, String sourceKey, String name,
            String mimeType, long size) {
        String key = "migrated/" + Instant.now().toString().substring(0, 10) + "/" + UUID.randomUUID() + "-" + name;
        try (ResponseInputStream<GetObjectResponse> stream = client(source).getObject(
                GetObjectRequest.builder().bucket(source.getBucket()).key(sourceKey).build())) {
            client(target).putObject(
                    PutObjectRequest.builder().bucket(target.getBucket()).key(key).contentType(mimeType).build(),
                    RequestBody.fromInputStream(stream, size));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not migrate object to target storage", exception);
        }
        target.setUsedBytes(target.getUsedBytes() + size);
        target.setBandwidthUsedBytes(target.getBandwidthUsedBytes() + size);
        nodes.save(target);
        return key;
    }

    public StoredContent get(StorageNode node, String key, String fallbackName, String fallbackMime, long fallbackSize) {
        ResponseInputStream<GetObjectResponse> response = client(node).getObject(GetObjectRequest.builder().bucket(node.getBucket()).key(key).build());
        GetObjectResponse metadata = response.response();
        long size = metadata.contentLength() == null ? fallbackSize : metadata.contentLength();
        node.setBandwidthUsedBytes(node.getBandwidthUsedBytes() + Math.max(0, size));
        nodes.save(node);
        return new StoredContent(fallbackName, metadata.contentType() == null ? fallbackMime : metadata.contentType(), metadata.contentLength() == null ? fallbackSize : metadata.contentLength(), fallbackSize, 0, fallbackSize - 1, false, response);
    }

    public StoredContent getRange(StorageNode node, String key, String fallbackName, String fallbackMime,
            long totalSize, long start, long end) {
        String range = "bytes=" + start + "-" + end;
        ResponseInputStream<GetObjectResponse> response = client(node).getObject(GetObjectRequest.builder().bucket(node.getBucket()).key(key).range(range).build());
        GetObjectResponse metadata = response.response();
        long size = end - start + 1;
        node.setBandwidthUsedBytes(node.getBandwidthUsedBytes() + Math.max(0, size));
        nodes.save(node);
        return new StoredContent(fallbackName, metadata.contentType() == null ? fallbackMime : metadata.contentType(), size, totalSize, start, end, true, response);
    }

    public void delete(StorageNode node, String key, long size) {
        client(node).deleteObject(DeleteObjectRequest.builder().bucket(node.getBucket()).key(key).build());
        node.setUsedBytes(Math.max(0, node.getUsedBytes() - size));
        nodes.save(node);
    }

    public void test(StorageNode node) {
        String key = ".haobox-health/" + UUID.randomUUID() + ".txt";
        S3Client s3 = client(node);
        try {
            s3.putObject(PutObjectRequest.builder().bucket(node.getBucket()).key(key).contentType("text/plain").build(),
                    RequestBody.fromString("ok"));
            s3.headObject(HeadObjectRequest.builder().bucket(node.getBucket()).key(key).build());
            s3.deleteObject(DeleteObjectRequest.builder().bucket(node.getBucket()).key(key).build());
            node.setLastCheckedAt(Instant.now());
            node.setLastError(null);
            nodes.save(node);
        } catch (S3Exception exception) {
            throw new IllegalStateException(describeS3Exception(exception));
        }
    }

    private String describeS3Exception(S3Exception exception) {
        String message = exception.awsErrorDetails() == null ? null : exception.awsErrorDetails().errorMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getMessage();
        }
        if (message == null || message.trim().isEmpty() || message.startsWith("null ")) {
            message = "S3 request was denied or the bucket settings are incorrect";
        }
        return "S3 test failed: " + message + " (status " + exception.statusCode() + ")";
    }

    private boolean hasCapacity(StorageNode node, long expectedBytes) {
        if (node.getCapacityBytes() <= 0) return true;
        long remaining = node.getCapacityBytes() - node.getUsedBytes();
        long softLimit = Math.max(MIN_FREE_BYTES, Math.round(node.getCapacityBytes() * (1D - SOFT_LIMIT_RATIO)));
        return remaining >= expectedBytes && remaining - expectedBytes >= softLimit;
    }

    private boolean hasBandwidth(StorageNode node, long expectedBytes) {
        if (node.getBandwidthLimitBytes() <= 0) return true;
        long remaining = node.getBandwidthLimitBytes() - node.getBandwidthUsedBytes();
        return remaining >= expectedBytes && node.getBandwidthUsedBytes() + expectedBytes <= Math.round(node.getBandwidthLimitBytes() * SOFT_LIMIT_RATIO);
    }

    private S3Client client(StorageNode node) {
        return S3Client.builder().endpointOverride(URI.create(node.getEndpoint())).region(Region.of(node.getRegion() == null ? "us-east-1" : node.getRegion())).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(node.getAccessKey(), node.getSecretKey()))).httpClient(ApacheHttpClient.builder().build()).forcePathStyle(true).build();
    }
    private String contentType(MultipartFile file) { return file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(); }

    public static class StoredContent {
        private final String name; private final String mimeType; private final Long size; private final long totalSize;
        private final long rangeStart; private final long rangeEnd; private final boolean partial; private final InputStream stream;
        public StoredContent(String name, String mimeType, Long size, long totalSize, long rangeStart, long rangeEnd, boolean partial, InputStream stream) { this.name = name; this.mimeType = mimeType; this.size = size; this.totalSize = totalSize; this.rangeStart = rangeStart; this.rangeEnd = rangeEnd; this.partial = partial; this.stream = stream; }
        public String getName() { return name; } public String getMimeType() { return mimeType; } public Long getSize() { return size; } public long getTotalSize() { return totalSize; } public long getRangeStart() { return rangeStart; } public long getRangeEnd() { return rangeEnd; } public boolean isPartial() { return partial; } public InputStream getStream() { return stream; }
    }
}
