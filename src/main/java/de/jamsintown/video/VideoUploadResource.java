package de.jamsintown.video;

import de.jamsintown.config.AppConfigService;
import de.jamsintown.story.StoryService;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Path("/api/v1/videos")
@RolesAllowed("user")
public class VideoUploadResource {

    private static final String DEFAULT_ALLOWED_TYPES = ".mp4,.mov,.webm,.avi";
    private static final long DEFAULT_MAX_SIZE = 524288000L; // 500 MB

    private final VideoService videoService;
    private final StoryService storyService;
    private final UserService userService;
    private final AppConfigService appConfigService;
    private final FfmpegService ffmpegService;
    private final MinioService minioService;
    private final io.vertx.mutiny.core.Vertx vertx;
    private final String defaultCapturesPath;

    @Inject
    @Channel("video-processing")
    Emitter<VideoProcessingMessage> videoProcessingEmitter;

    @Inject
    public VideoUploadResource(VideoService videoService, StoryService storyService,
                                UserService userService, AppConfigService appConfigService,
                                FfmpegService ffmpegService, MinioService minioService,
                                io.vertx.mutiny.core.Vertx vertx,
                                @ConfigProperty(name = "jahrbuch.captures.path") String defaultCapturesPath) {
        this.videoService = videoService;
        this.storyService = storyService;
        this.userService = userService;
        this.appConfigService = appConfigService;
        this.ffmpegService = ffmpegService;
        this.minioService = minioService;
        this.vertx = vertx;
        this.defaultCapturesPath = defaultCapturesPath;
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Video> uploadVideo(MultipartFormDataInput input) {
        return loadUploadConfig().chain(config -> doUpload(input, config));
    }

    @POST
    @Path("/upload/chunk")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> uploadChunk(MultipartFormDataInput input) {
        return loadUploadConfig().chain(config ->
            userService.getCurrentUser().chain(user -> {
                try {
                    Map<String, List<FormValue>> parts = new HashMap<>();
                    input.getValues().forEach((k, v) -> parts.put(k, new ArrayList<>(v)));

                    final String uploadId = getFormValue(parts, "uploadId");
                    final int chunkIndex = Integer.parseInt(getFormValue(parts, "chunkIndex").trim());
                    final int totalChunks = Integer.parseInt(getFormValue(parts, "totalChunks").trim());
                    final String fileName = getFormValue(parts, "fileName");

                    if (!uploadId.matches("[a-zA-Z0-9\\-]{8,64}")) {
                        return Uni.createFrom().item(
                            Response.status(400).entity(Map.of("message", "Ungültige Upload-ID")).build());
                    }

                    List<FormValue> fileParts = parts.get("file");
                    if (fileParts == null || fileParts.isEmpty()) {
                        return Uni.createFrom().item(
                            Response.status(400).entity(Map.of("message", "Kein Chunk")).build());
                    }

                    final InputStream chunkStream = fileParts.get(0).getFileItem().getInputStream();
                    final io.vertx.core.Context vertxCtx = io.vertx.core.Vertx.currentContext();

                    return Uni.createFrom().<Boolean>emitter(emitter ->
                        io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                            try {
                                java.nio.file.Path tmpDir = Paths.get(config.capturesPath, "videos", "tmp", uploadId);
                                Files.createDirectories(tmpDir);
                                java.nio.file.Path chunkPath = tmpDir.resolve(String.format("chunk_%05d", chunkIndex));
                                try (chunkStream) {
                                    Files.copy(chunkStream, chunkPath, StandardCopyOption.REPLACE_EXISTING);
                                }
                                long receivedCount = Files.list(tmpDir)
                                    .filter(p -> p.getFileName().toString().startsWith("chunk_"))
                                    .count();
                                vertxCtx.runOnContext(v -> emitter.complete(receivedCount >= totalChunks));
                            } catch (Exception e) {
                                vertxCtx.runOnContext(v -> emitter.fail(e));
                            }
                        })
                    ).chain(isLast -> {
                        if (isLast) {
                            return assembleAndCreate(uploadId, totalChunks, fileName, config, user, parts);
                        }
                        return Uni.createFrom().item(
                            Response.ok(Map.of("received", chunkIndex, "total", totalChunks)).build());
                    }).onFailure().recoverWithItem(e -> {
                        log.error("Chunk-Upload Fehler: {}", e.getMessage(), e);
                        return Response.serverError().entity(Map.of("message", "Chunk-Fehler: " + e.getMessage())).build();
                    });

                } catch (Exception e) {
                    log.error("Chunk-Upload Fehler: {}", e.getMessage(), e);
                    return Uni.createFrom().item(
                        Response.serverError().entity(Map.of("message", "Chunk-Fehler: " + e.getMessage())).build());
                }
            })
        );
    }

    private Uni<Response> assembleAndCreate(String uploadId, int totalChunks, String fileName,
                                             UploadConfig config, de.jamsintown.user.User user,
                                             Map<String, List<FormValue>> parts) {
        String title, description, storyIdStr;
        try {
            title = getFormValue(parts, "title");
            description = getFormValue(parts, "description");
            storyIdStr = getFormValue(parts, "storyId");
        } catch (Exception e) {
            return Uni.createFrom().item(Response.serverError().entity(Map.of("message", e.getMessage())).build());
        }

        String ext = getFileExtension(fileName != null && !fileName.isBlank() ? fileName : "video.mp4");
        if (!isAllowedType(ext, config.allowedTypes)) {
            cleanupTmp(config.capturesPath, uploadId);
            return Uni.createFrom().item(
                Response.status(400).entity(Map.of("message", "Nicht erlaubter Typ: " + ext)).build());
        }

        String subDir = (user.activeGroup != null)
            ? "videos/gruppen/" + user.activeGroup.id + "/"
            : "videos/ungrouped/";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueFileName = timestamp + "_" + UUID.randomUUID() + ext;
        final String videoTitle = title != null && !title.isBlank() ? title : fileName;
        final String videoDesc = description;
        final String sid = storyIdStr;

        io.vertx.core.Context vertxContext = io.vertx.core.Vertx.currentContext();
        return Uni.createFrom().<Object[]>emitter(emitter ->
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    java.nio.file.Path dirPath = Paths.get(config.capturesPath, subDir);
                    Files.createDirectories(dirPath);
                    java.nio.file.Path finalPath = dirPath.resolve(uniqueFileName);
                    java.nio.file.Path tmpDir = Paths.get(config.capturesPath, "videos", "tmp", uploadId);
                    try (OutputStream out = Files.newOutputStream(finalPath)) {
                        for (int i = 0; i < totalChunks; i++) {
                            Files.copy(tmpDir.resolve(String.format("chunk_%05d", i)), out);
                        }
                    }
                    cleanupTmp(config.capturesPath, uploadId);
                    ZonedDateTime capturedAt = ffmpegService.readCreationTime(finalPath);
                    String metadata = ffmpegService.readMetadataJson(finalPath);
                    long fileSize = finalPath.toFile().length();
                    InputStream videoStream = java.nio.file.Files.newInputStream(finalPath);
                    vertxContext.runOnContext(v -> emitter.complete(new Object[]{finalPath, capturedAt, metadata, fileSize, videoStream}));
                } catch (Exception e) {
                    vertxContext.runOnContext(v -> emitter.fail(e));
                }
            })
        ).chain(result -> {
            java.nio.file.Path finalPath = (java.nio.file.Path) result[0];
            ZonedDateTime capturedAt = (ZonedDateTime) result[1];
            String metadata = (String) result[2];
            long fileSize = (long) result[3];
            InputStream videoStream = (InputStream) result[4];
            String objectKey = subDir + uniqueFileName;

            return minioService.upload(objectKey, videoStream, fileSize, "video/mp4")
                    .invoke(() -> {
                        try { Files.deleteIfExists(finalPath); } catch (Exception ignored) {}
                    })
                    .chain(() -> {
                        Video video = new Video();
                        video.pfad = "/" + objectKey;
                        video.title = videoTitle;
                        video.description = videoDesc;
                        video.priority = 3;
                        video.capturedAt = capturedAt != null ? capturedAt : ZonedDateTime.now();
                        video.processingStatus = VideoProcessingStatus.PENDING;
                        if (metadata != null) video.metadata = metadata;

                        Long storyId = null;
                        if (sid != null && !sid.isBlank()) {
                            try { storyId = Long.parseLong(sid); } catch (NumberFormatException ignored) {}
                        }
                        final Long storyIdLong = storyId;
                        if (storyIdLong != null) {
                            return storyService.findById(storyIdLong).chain(story -> {
                                if (story != null) video.story = story;
                                return videoService.create(video);
                            }).invoke(v -> emitProcessingMessage(v)).map(v -> Response.ok(v).build());
                        }
                        return videoService.create(video)
                                .invoke(v -> emitProcessingMessage(v))
                                .map(v -> Response.ok(v).build());
                    });
        }).onFailure().recoverWithItem(e -> {
            log.error("Assembly-Fehler: {}", e.getMessage(), e);
            cleanupTmp(config.capturesPath, uploadId);
            return Response.serverError().entity(Map.of("message", "Assembly-Fehler: " + e.getMessage())).build();
        });
    }

    private void cleanupTmp(String capturesPath, String uploadId) {
        try {
            java.nio.file.Path tmpDir = Paths.get(capturesPath, "videos", "tmp", uploadId);
            if (Files.exists(tmpDir)) {
                Files.walk(tmpDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }
        } catch (Exception e) {
            log.warn("Tmp-Verzeichnis konnte nicht gelöscht werden: {}", e.getMessage());
        }
    }

    private Uni<UploadConfig> loadUploadConfig() {
        return appConfigService.getValue("jahrbuch.captures.path")
                .chain(pathStr -> appConfigService.getValue("jahrbuch.upload.video.max-size")
                        .chain(maxStr -> appConfigService.getValue("jahrbuch.upload.video.allowed-types")
                                .map(typesStr -> new UploadConfig(
                                        pathStr != null ? pathStr : defaultCapturesPath,
                                        maxStr != null ? Long.parseLong(maxStr) : DEFAULT_MAX_SIZE,
                                        typesStr != null ? typesStr : DEFAULT_ALLOWED_TYPES
                                ))));
    }

    private Uni<Video> doUpload(MultipartFormDataInput input, UploadConfig config) {
        return userService.getCurrentUser().chain(user -> {
            try {
                Map<String, List<FormValue>> parts = new HashMap<>();
                input.getValues().forEach((k, v) -> parts.put(k, new ArrayList<>(v)));

                List<FormValue> fileParts = parts.get("file");
                String title = getFormValue(parts, "title");
                String description = getFormValue(parts, "description");
                String storyIdStr = getFormValue(parts, "storyId");

                if (fileParts == null || fileParts.isEmpty()) {
                    return Uni.createFrom().failure(
                            new WebApplicationException("Keine Datei gefunden", Response.Status.BAD_REQUEST));
                }

                FormValue filePart = fileParts.get(0);
                String fileName = filePart.getFileName();
                String ext = getFileExtension(fileName);

                if (!isAllowedType(ext, config.allowedTypes)) {
                    return Uni.createFrom().failure(
                            new WebApplicationException("Nicht erlaubter Typ: " + ext, Response.Status.BAD_REQUEST));
                }
                if (filePart.getFileItem().getFileSize() > config.maxSize) {
                    return Uni.createFrom().failure(
                            new WebApplicationException("Datei zu groß (max. " + config.maxSize / 1024 / 1024 + " MB)",
                                    Response.Status.BAD_REQUEST));
                }

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String uniqueFileName = timestamp + "_" + UUID.randomUUID() + ext;
                String subDir = (user.activeGroup != null)
                        ? "videos/gruppen/" + user.activeGroup.id + "/"
                        : "videos/ungrouped/";

                final String finalTitle = title != null && !title.isBlank() ? title : fileName;
                final String finalDesc = description;
                final String finalStoryIdStr = storyIdStr;
                final InputStream fileStream = filePart.getFileItem().getInputStream();

                return vertx.<Object[]>executeBlocking(() -> {
                    java.nio.file.Path dirPath = Paths.get(config.capturesPath, subDir);
                    Files.createDirectories(dirPath);
                    java.nio.file.Path targetPath = dirPath.resolve(uniqueFileName);
                    try (fileStream) {
                        Files.copy(fileStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    ZonedDateTime capturedAt = ffmpegService.readCreationTime(targetPath);
                    String metadata = ffmpegService.readMetadataJson(targetPath);
                    long fileSize = targetPath.toFile().length();
                    InputStream videoStream = java.nio.file.Files.newInputStream(targetPath);
                    return new Object[]{targetPath, capturedAt, metadata, fileSize, videoStream};
                }).chain(result -> {
                    java.nio.file.Path targetPath = (java.nio.file.Path) result[0];
                    ZonedDateTime capturedAt = (ZonedDateTime) result[1];
                    String metadata = (String) result[2];
                    long fileSize = (long) result[3];
                    InputStream videoStream = (InputStream) result[4];
                    String objectKey = subDir + uniqueFileName;

                    return minioService.upload(objectKey, videoStream, fileSize, "video/mp4")
                            .invoke(() -> {
                                try { Files.deleteIfExists(targetPath); } catch (Exception ignored) {}
                            })
                            .chain(() -> {
                                Video video = new Video();
                                video.pfad = "/" + objectKey;
                                video.title = finalTitle;
                                video.description = finalDesc;
                                video.priority = 3;
                                video.capturedAt = capturedAt != null ? capturedAt : ZonedDateTime.now();
                                video.processingStatus = VideoProcessingStatus.PENDING;
                                if (metadata != null) video.metadata = metadata;

                                Long storyId = null;
                                if (finalStoryIdStr != null && !finalStoryIdStr.isBlank()) {
                                    try { storyId = Long.parseLong(finalStoryIdStr); } catch (NumberFormatException ignored) {}
                                }
                                if (storyId != null) {
                                    final Long sid = storyId;
                                    return storyService.findById(sid).chain(story -> {
                                        if (story != null) video.story = story;
                                        return videoService.create(video);
                                    }).invoke(v -> emitProcessingMessage(v));
                                }
                                return videoService.create(video).invoke(v -> emitProcessingMessage(v));
                            });
                });

            } catch (Exception e) {
                log.error("Fehler beim Video-Upload: {}", e.getMessage(), e);
                return Uni.createFrom().failure(
                        new WebApplicationException("Upload-Fehler: " + e.getMessage(),
                                Response.Status.INTERNAL_SERVER_ERROR));
            }
        });
    }

    @POST
    @Path("/backfill-captured-at")
    @RolesAllowed("admin")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> backfillCapturedAt() {
        return loadUploadConfig()
                .chain(config -> videoService.listWithoutCapturedAt()
                        .chain(videos -> vertx.<List<Video>>executeBlocking(() -> {
                            for (Video video : videos) {
                                java.nio.file.Path videoPath = Paths.get(config.capturesPath).resolve(video.pfad.substring(1));
                                if (videoPath.toFile().exists()) {
                                    ZonedDateTime t = ffmpegService.readCreationTime(videoPath);
                                    video.capturedAt = t != null ? t : video.created;
                                } else {
                                    video.capturedAt = video.created;
                                }
                            }
                            return videos;
                        }))
                        .chain(videos -> videoService.backfillCapturedAt(videos))
                        .map(count -> Response.ok("capturedAt befüllt: " + count).build()));
    }

    private String getFormValue(Map<String, List<FormValue>> parts, String key) throws Exception {
        List<FormValue> list = parts.get(key);
        return (list != null && !list.isEmpty()) ? list.get(0).getValue() : "";
    }

    private String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i > 0 ? fileName.substring(i).toLowerCase() : "";
    }

    private boolean isAllowedType(String ext, String allowedTypes) {
        if (ext.isEmpty()) return false;
        return Arrays.stream(allowedTypes.split(",")).anyMatch(t -> t.trim().equalsIgnoreCase(ext));
    }

    private void emitProcessingMessage(Video video) {
        String objectKey = video.pfad.startsWith("/") ? video.pfad.substring(1) : video.pfad;
        videoProcessingEmitter.send(new VideoProcessingMessage(video.id, objectKey));
        log.info("Video-Processing-Message gesendet für Video {}: {}", video.id, objectKey);
    }

    private static class UploadConfig {
        String capturesPath;
        long maxSize;
        String allowedTypes;

        UploadConfig(String capturesPath, long maxSize, String allowedTypes) {
            this.capturesPath = capturesPath;
            this.maxSize = maxSize;
            this.allowedTypes = allowedTypes;
        }
    }
}
