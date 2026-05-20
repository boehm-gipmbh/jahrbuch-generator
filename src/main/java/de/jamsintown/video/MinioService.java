package de.jamsintown.video;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;

@Slf4j
@ApplicationScoped
public class MinioService {

    private final MinioClient client;
    private final String bucket;

    public MinioService(
            @ConfigProperty(name = "minio.endpoint") String endpoint,
            @ConfigProperty(name = "minio.access-key") String accessKey,
            @ConfigProperty(name = "minio.secret-key") String secretKey,
            @ConfigProperty(name = "minio.bucket", defaultValue = "captures") String bucket) {
        this.bucket = bucket;
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public Uni<Void> upload(String objectKey, InputStream data, long contentLength, String contentType) {
        return Uni.createFrom().<Void>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    client.putObject(PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(data, contentLength, -1)
                            .contentType(contentType)
                            .build());
                    emitter.complete(null);
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        });
    }

    public Uni<GetObjectResponse> download(String objectKey, String rangeHeader) {
        return Uni.createFrom().<GetObjectResponse>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    GetObjectArgs.Builder builder = GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey);
                    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                        String spec = rangeHeader.substring(6);
                        String[] parts = spec.split("-", 2);
                        if (!parts[0].isEmpty()) {
                            long offset = Long.parseLong(parts[0]);
                            builder.offset(offset);
                            if (!parts[1].isEmpty()) {
                                long end = Long.parseLong(parts[1]);
                                builder.length(end - offset + 1);
                            }
                        }
                    }
                    emitter.complete(client.getObject(builder.build()));
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        });
    }

    public Uni<Long> getObjectSize(String objectKey) {
        return Uni.createFrom().<Long>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    StatObjectResponse stat = client.statObject(
                            StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
                    emitter.complete(stat.size());
                } catch (ErrorResponseException e) {
                    if ("NoSuchKey".equals(e.errorResponse().code())) {
                        emitter.complete(-1L);
                    } else {
                        emitter.fail(e);
                    }
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        });
    }

    public Uni<Void> delete(String objectKey) {
        return Uni.createFrom().<Void>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    client.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
                    emitter.complete(null);
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        });
    }
}
