package de.jamsintown.bild;

import de.jamsintown.config.AppConfigService;
import de.jamsintown.dtos.RotationDTO;
import de.jamsintown.dtos.UploadConfigDTO;
import de.jamsintown.story.StoryService;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.ws.rs.core.MediaType;

import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Slf4j
@Path("/api/v1/bilder")
@RolesAllowed("user")
public class BilderUploadResource {

    private final BildService bildService;
    private final StoryService storyService;
    private final UserService userService;
    private final io.vertx.mutiny.core.Vertx vertx;
    private final AppConfigService appConfigService;
    private final String defaultCapturesPath;

    @Inject
    public BilderUploadResource(BildService bildService, StoryService storyService, UserService userService,
                                io.vertx.mutiny.core.Vertx vertx,
                                AppConfigService appConfigService,
                                @ConfigProperty(name = "jahrbuch.captures.path") String defaultCapturesPath) {
        this.bildService = bildService;
        this.storyService = storyService;
        this.userService = userService;
        this.vertx = vertx;
        this.appConfigService = appConfigService;
        this.defaultCapturesPath = defaultCapturesPath;
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Bild> uploadBild(MultipartFormDataInput input) {
        // Config-Werte vorab laden
        return loadUploadConfig()
                .chain(config -> doUploadBild(input, config));
    }

    private Uni<UploadConfig> loadUploadConfig() {
        return appConfigService.getValue("jahrbuch.upload.max-size")
                .chain(maxSizeStr -> appConfigService.getValue("jahrbuch.upload.allowed-types")
                        .chain(allowedStr -> appConfigService.getValue("jahrbuch.captures.path")
                                .map(pathStr -> new UploadConfig(
                                        Long.parseLong(maxSizeStr != null ? maxSizeStr : "2097152"),
                                        allowedStr != null ? allowedStr : ".jpg,.jpeg,.png,.gif,.bmp,.webp,.tiff,.tif",
                                        pathStr != null ? pathStr : defaultCapturesPath
                                ))
                        )
                );
    }

    private Uni<Bild> doUploadBild(MultipartFormDataInput input, UploadConfig config) {
        return userService.getCurrentUser().chain(user -> doUploadBildForUser(input, config, user));
    }

    private Uni<Bild> doUploadBildForUser(MultipartFormDataInput input, UploadConfig config, de.jamsintown.user.User user) {
        Map<String, Collection<FormValue>> formValues = input.getValues();
        Map<String, List<FormValue>> formParts = new HashMap<>();
        formValues.forEach((key, collection) -> formParts.put(key, new ArrayList<>(collection)));

        List<FormValue> fileParts = formParts.get("file");
        if (fileParts == null || fileParts.isEmpty()) {
            return Uni.createFrom().failure(
                    new WebApplicationException("Keine Datei gefunden", Response.Status.BAD_REQUEST));
        }

        FormValue filePart = fileParts.get(0);
        String fileName = filePart.getFileName();
        String fileExtension = getFileExtension(fileName);

        if (!isAllowedFileType(fileExtension, config.allowedFileTypes)) {
            return Uni.createFrom().failure(
                    new WebApplicationException(
                            "Nicht unterstütztes Dateiformat. Erlaubte Formate: " + config.allowedFileTypes,
                            Response.Status.BAD_REQUEST));
        }

        long fileSize;
        try { fileSize = filePart.getFileItem().getFileSize(); }
        catch (Exception e) { fileSize = Long.MAX_VALUE; }
        if (fileSize > config.maxUploadSize) {
            return Uni.createFrom().failure(
                    new WebApplicationException(
                            "Datei zu groß. Maximale Größe: " + (config.maxUploadSize / 1024 / 1024) + "MB",
                            Response.Status.BAD_REQUEST));
        }

        String title, description, storyIdStr, capturedAtStr, exifDataStr;
        try {
            title = getFormValue(formParts, "title");
            description = getFormValue(formParts, "description");
            storyIdStr = getFormValue(formParts, "storyId");
            capturedAtStr = getFormValue(formParts, "capturedAt");
            exifDataStr = getFormValue(formParts, "exifData");
        } catch (Exception e) {
            return Uni.createFrom().failure(
                    new WebApplicationException("Fehler beim Lesen der Formulardaten: " + e.getMessage(),
                            Response.Status.BAD_REQUEST));
        }
        Long storyId = null;
        if (storyIdStr != null && !storyIdStr.isEmpty()) {
            try { storyId = Long.parseLong(storyIdStr); }
            catch (NumberFormatException e) { log.error("Ungültige Story-ID: {}", storyIdStr); }
        }
        // EXIF-Datum vom Frontend (vor Komprimierung gelesen, Format: "yyyy:MM:dd HH:mm:ss")
        ZonedDateTime capturedAtFromFrontend = null;
        if (capturedAtStr != null && !capturedAtStr.isEmpty()) {
            try {
                capturedAtFromFrontend = LocalDateTime.parse(capturedAtStr.trim(),
                        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")).atZone(ZoneId.systemDefault());
            } catch (Exception e) {
                log.debug("capturedAt vom Frontend nicht parsebar: {}", capturedAtStr);
            }
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueFileName = timestamp + "_" + UUID.randomUUID() + fileExtension;
        String subDir = (user.activeGroup != null)
                ? "gruppen/" + user.activeGroup.id + "/"
                : "ungrouped/";

        final String finalTitle = title;
        final String finalDescription = description;
        final Long finalStoryId = storyId;
        final ZonedDateTime finalCapturedAtFromFrontend = capturedAtFromFrontend;
        final String finalExifData = (exifDataStr != null && !exifDataStr.isEmpty()) ? exifDataStr : null;

        // Datei-I/O auf Worker-Thread auslagern — Event-Loop nicht blockieren
        return executeBlockingFileIO(() -> {
            java.nio.file.Path dirPath = Paths.get(config.capturesPath, subDir);
            Files.createDirectories(dirPath);
            java.nio.file.Path targetPath = dirPath.resolve(uniqueFileName);
            try (InputStream fileInputStream = filePart.getFileItem().getInputStream()) {
                Files.copy(fileInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            generateThumbnail(targetPath);
            // Frontend-EXIF hat Vorrang (vor Komprimierung gelesen), Fallback auf gespeicherte Datei
            ZonedDateTime capturedAt = finalCapturedAtFromFrontend != null
                    ? finalCapturedAtFromFrontend
                    : readExifCapturedAt(targetPath);
            return new Object[]{targetPath, capturedAt};
        }).chain(result -> {
            ZonedDateTime capturedAt = (ZonedDateTime) result[1];

            Bild bild = new Bild();
            bild.setPfad("/" + subDir + uniqueFileName);
            bild.setTitle(finalTitle);
            bild.setDescription(finalDescription);
            bild.setPriority(3);
            bild.setCapturedAt(capturedAt != null ? capturedAt : ZonedDateTime.now());
            String exifJson = finalExifData != null ? finalExifData : ExifUtils.readExifJson((java.nio.file.Path) result[0]);
            if (exifJson != null) bild.setExifData(exifJson.replaceAll("[\u0000-\u0008\u000B\u000C\u000E-\u001F]", ""));

            if (finalStoryId != null) {
                return storyService.findById(finalStoryId)
                        .onItem().transformToUni(story -> {
                            if (story != null) bild.setStory(story);
                            return bildService.create(bild);
                        });
            }
            return bildService.create(bild);
        });
    }

    /**
     * Endpunkt zum Abrufen der Upload-Konfigurationen für das Frontend
     */
    @GET
    @Path("/uploadconfig")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UploadConfigDTO> getUploadConfig() {
        return loadUploadConfig()
                .map(config -> {
                    List<String> allowedTypesList = Arrays.asList(config.allowedFileTypes.split(","));
                    return new UploadConfigDTO(config.maxUploadSize, allowedTypesList);
                });
    }

    /**
     * Einfacher Datei-Upload-Endpunkt, der eine Datei entgegennimmt und im Verzeichnis speichert.
     * Der Dateiname wird beibehalten.
     */
    @POST
    @Path("/uploadcapture")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Response> uploadFile(MultipartFormDataInput input) {
        return appConfigService.getValue("jahrbuch.captures.path")
                .map(pathStr -> pathStr != null ? pathStr : "/data/captures/")
                .onItem().transform(capturesPath -> {
                    try {
                        Map<String, Collection<FormValue>> formValues = input.getValues();
                        List<FormValue> fileParts = new ArrayList<>(formValues.get("file"));
                        if (fileParts.isEmpty()) {
                            return Response.status(Response.Status.BAD_REQUEST).entity("Keine Datei gefunden").build();
                        }
                        FormValue filePart = fileParts.get(0);
                        String fileName = filePart.getFileName();
                        try (InputStream fileInputStream = filePart.getFileItem().getInputStream()) {
                            java.nio.file.Path target = java.nio.file.Paths.get(capturesPath, fileName);
                            java.nio.file.Files.copy(fileInputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            generateThumbnail(target);
                        }
                        return Response.ok("File uploaded").build();
                    } catch (Exception e) {
                        return Response.serverError().entity("Upload failed: " + e.getMessage()).build();
                    }
                });
    }



    @POST
    @Path("/{id}/rotate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Bild> rotateBild(@PathParam("id") Long id, RotationDTO rotation) {
        return appConfigService.getValue("jahrbuch.captures.path")
                .map(pathStr -> pathStr != null ? pathStr : "/data/captures/")
                .chain(capturesPath -> bildService.findById(id)
                        .onItem().transformToUni(bild -> {
                            java.nio.file.Path imagePath = Paths.get(capturesPath).resolve(bild.getPfad().substring(1));
                            // Datei-I/O auf Vert.x Worker-Thread auslagern, DB-Operationen bleiben auf dem EventLoop
                            return vertx.<Bild>executeBlocking(() -> {
                                        try {
                                            rotateImageFile(imagePath, rotation.getDegrees());
                                            generateThumbnail(imagePath);
                                        } catch (Exception e) {
                                            throw new WebApplicationException("Fehler beim Rotieren: " + e.getMessage(),
                                                    Response.Status.INTERNAL_SERVER_ERROR);
                                        }
                                        return bild;
                                    })
                                    .onItem().transformToUni(b -> {
                                        b.setLastRotated(System.currentTimeMillis());
                                        return bildService.update(b);
                                    });
                        }));
    }

    @POST
    @Path("/generate-thumbs")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> generateThumbs() {
        return appConfigService.getValue("jahrbuch.captures.path")
                .map(pathStr -> pathStr != null ? pathStr : "/data/captures/")
                .chain(capturesPath -> bildService.listForUser()
                        .chain(bilder -> vertx.<Integer>executeBlocking(() -> {
                            int count = 0;
                            for (Bild bild : bilder) {
                                java.nio.file.Path originalPath = Paths.get(capturesPath).resolve(bild.getPfad().substring(1));
                                java.nio.file.Path thumbPath = Paths.get(capturesPath).resolve(toThumbName(originalPath.getFileName().toString()));
                                if (originalPath.toFile().exists() && !thumbPath.toFile().exists()) {
                                    try {
                                        generateThumbnail(originalPath);
                                        count++;
                                    } catch (Exception e) {
                                        log.warn("Thumbnail-Generierung fehlgeschlagen für {}: {}", bild.getPfad(), e.getMessage());
                                    }
                                }
                            }
                            return count;
                        }))
                        .map(count -> Response.ok("Thumbnails generiert: " + count).build()));
    }

    @POST
    @Path("/backfill-captured-at")
    @RolesAllowed("admin")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> backfillCapturedAt() {
        return appConfigService.getValue("jahrbuch.captures.path")
                .map(pathStr -> pathStr != null ? pathStr : "/data/captures/")
                .chain(capturesPath -> bildService.listWithoutCapturedAt()
                        .chain(bilder -> vertx.<java.util.List<Bild>>executeBlocking(() -> {
                            for (Bild bild : bilder) {
                                java.nio.file.Path imagePath = Paths.get(capturesPath).resolve(bild.getPfad().substring(1));
                                if (imagePath.toFile().exists()) {
                                    ZonedDateTime exif = readExifCapturedAt(imagePath);
                                    bild.setCapturedAt(exif != null ? exif : bild.created);
                                } else {
                                    bild.setCapturedAt(bild.created);
                                }
                            }
                            return bilder;
                        }))
                        .chain(bilder -> bildService.backfillCapturedAt(bilder))
                        .map(count -> Response.ok("capturedAt befüllt: " + count).build()));
    }

    public static String toThumbName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base + "_thumb.jpg";
    }

    protected <T> Uni<T> executeBlockingFileIO(java.util.concurrent.Callable<T> callable) {
        return vertx.executeBlocking(callable);
    }

    void generateThumbnail(java.nio.file.Path originalPath) throws Exception {
        java.nio.file.Path thumbPath = originalPath.getParent().resolve(toThumbName(originalPath.getFileName().toString()));
        Process process = new ProcessBuilder(
                "convert", "-auto-orient", originalPath.toString(), "-resize", "400x>", thumbPath.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Thumbnail-Generierung fehlgeschlagen (ImageMagick): " + output);
        }
    }

    private ZonedDateTime readExifCapturedAt(java.nio.file.Path imagePath) {
        return ExifUtils.readCapturedAt(imagePath);
    }

    private void rotateImageFile(java.nio.file.Path imagePath, int degrees) throws Exception {
        degrees = ((degrees % 360) + 360) % 360;
        if (degrees == 0) return;

        // jpegtran: verlustfreie JPEG-Rotation, erhält EXIF vollständig (-copy all)
        String rotate = switch (degrees) {
            case 90 -> "90";
            case 180 -> "180";
            case 270 -> "270";
            default -> throw new IOException("Ungültiger Rotationswinkel: " + degrees);
        };
        java.nio.file.Path tmpPath = imagePath.resolveSibling(imagePath.getFileName() + ".rotating.jpg");
        try {
            Process process = new ProcessBuilder(
                    "jpegtran", "-rotate", rotate, "-copy", "all", "-outfile", tmpPath.toString(), imagePath.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Bildrotation fehlgeschlagen (jpegtran): " + output);
            }
            Files.move(tmpPath, imagePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpPath);
        }
    }

    private String getFormValue(Map<String, List<FormValue>> formParts, String key) throws Exception {
        List<FormValue> parts = formParts.get(key);
        if (parts != null && !parts.isEmpty()) {
            return parts.get(0).getValue();
        }
        return "";
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot);
        }
        return "";
    }

    private boolean isAllowedFileType(String fileExtension, String allowedFileTypes) {
        if (fileExtension.isEmpty()) {
            return false;
        }
        String[] allowedTypes = allowedFileTypes.split(",");
        for (String type : allowedTypes) {
            if (fileExtension.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    /** Hilfsklasse für Upload-Konfiguration */
    private static class UploadConfig {
        long maxUploadSize;
        String allowedFileTypes;
        String capturesPath;

        UploadConfig(long maxUploadSize, String allowedFileTypes, String capturesPath) {
            this.maxUploadSize = maxUploadSize;
            this.allowedFileTypes = allowedFileTypes;
            this.capturesPath = capturesPath;
        }
    }
}