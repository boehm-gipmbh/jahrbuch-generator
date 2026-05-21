package de.jamsintown.video;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;

@Slf4j
@ApplicationScoped
public class MinioService {

    @Inject
    S3Client s3;

    @ConfigProperty(name = "minio.bucket", defaultValue = "captures")
    String bucket;

    public Uni<Void> upload(String objectKey, InputStream data, long contentLength, String contentType) {
        Context ctx = Vertx.currentContext();
        return Uni.createFrom().<Void>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .contentLength(contentLength)
                            .build(),
                            RequestBody.fromInputStream(data, contentLength));
                    dispatch(ctx, () -> emitter.complete(null));
                } catch (Exception e) {
                    dispatch(ctx, () -> emitter.fail(e));
                }
            });
        });
    }

    public Uni<Long> getObjectSize(String objectKey) {
        Context ctx = Vertx.currentContext();
        return Uni.createFrom().<Long>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build());
                    dispatch(ctx, () -> emitter.complete(head.contentLength()));
                } catch (NoSuchKeyException e) {
                    dispatch(ctx, () -> emitter.complete(-1L));
                } catch (S3Exception e) {
                    if (e.statusCode() == 404) {
                        dispatch(ctx, () -> emitter.complete(-1L));
                    } else {
                        dispatch(ctx, () -> emitter.fail(e));
                    }
                } catch (Exception e) {
                    dispatch(ctx, () -> emitter.fail(e));
                }
            });
        });
    }

    public Uni<InputStream> download(String objectKey, String rangeHeader) {
        Context ctx = Vertx.currentContext();
        return Uni.createFrom().<InputStream>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    GetObjectRequest.Builder builder = GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey);
                    if (rangeHeader != null && !rangeHeader.isBlank()) {
                        builder.range(rangeHeader);
                    }
                    InputStream stream = s3.getObject(builder.build(), ResponseTransformer.toInputStream());
                    dispatch(ctx, () -> emitter.complete(stream));
                } catch (Exception e) {
                    dispatch(ctx, () -> emitter.fail(e));
                }
            });
        });
    }

    public Uni<Void> delete(String objectKey) {
        Context ctx = Vertx.currentContext();
        return Uni.createFrom().<Void>emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    s3.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build());
                    dispatch(ctx, () -> emitter.complete(null));
                } catch (Exception e) {
                    dispatch(ctx, () -> emitter.fail(e));
                }
            });
        });
    }

    private static void dispatch(Context ctx, Runnable action) {
        if (ctx != null) {
            ctx.runOnContext(v -> action.run());
        } else {
            action.run();
        }
    }
}
