package com.vorconcierge.ragcore.service;

import com.vorconcierge.ragcore.config.ConciergeProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final String storageType;
    private final Path localPath;
    private final MinioClient minioClient;
    private final String bucket;

    public StorageService(ConciergeProperties properties) {
        this.storageType = properties.storage().type();
        this.localPath = Paths.get(properties.storage().localPath());
        this.bucket = properties.storage().minio().bucket();
        MinioClient client = null;
        if ("minio".equalsIgnoreCase(storageType)) {
            var m = properties.storage().minio();
            client = MinioClient.builder()
                    .endpoint(m.endpoint())
                    .credentials(m.accessKey(), m.secretKey())
                    .build();
            ensureBucket(client);
        }
        this.minioClient = client;
        if ("local".equalsIgnoreCase(storageType)) {
            try {
                Files.createDirectories(localPath);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create local storage path", e);
            }
        }
    }

    public String store(String hash, String filename, InputStream input, long size) {
        String objectPath = hash + "/" + filename;
        if ("minio".equalsIgnoreCase(storageType)) {
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectPath)
                        .stream(input, size, -1)
                        .build());
                return "minio://" + bucket + "/" + objectPath;
            } catch (Exception e) {
                throw new IllegalStateException("MinIO upload failed", e);
            }
        }
        try {
            Path targetDir = localPath.resolve(hash);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(filename);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new IllegalStateException("Local storage failed", e);
        }
    }

    public InputStream retrieve(String uri) {
        if (uri.startsWith("minio://")) {
            String objectPath = uri.substring(("minio://" + bucket + "/").length());
            try {
                return minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectPath)
                        .build());
            } catch (Exception e) {
                throw new IllegalStateException("MinIO download failed", e);
            }
        }
        try {
            return Files.newInputStream(Paths.get(uri));
        } catch (Exception e) {
            throw new IllegalStateException("Local file read failed", e);
        }
    }

    public void delete(String uri) {
        if (uri.startsWith("minio://")) {
            String objectPath = uri.substring(("minio://" + bucket + "/").length());
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectPath)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to delete minio object: {}", uri, e);
            }
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(uri));
        } catch (Exception e) {
            log.warn("Failed to delete local file: {}", uri, e);
        }
    }

    private void ensureBucket(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check failed: {}", e.getMessage());
        }
    }
}
