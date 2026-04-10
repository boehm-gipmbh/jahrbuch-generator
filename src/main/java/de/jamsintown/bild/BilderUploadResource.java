package de.jamsintown.bild;

import de.jamsintown.config.AppConfigService;
import de.jamsintown.dtos.RotationDTO;
import de.jamsintown.dtos.UploadConfigDTO;
import de.jamsintown.story.StoryService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Slf4j
@Path("/api/v1/bilder")
@RolesAllowed("user")
public class BilderUploadResource {

    private final BildService bildService;
    private final StoryService storyService;
    private final io.vertx.mutiny.core.Vertx vertx;
    private final AppConfigService appConfigService;

    @Inject
    public BilderUploadResource(BildService bildService, StoryService storyService, io.vertx.mutiny.core.Vertx vertx,
                                AppConfigService appConfigService) {
        this.bildService = bildService;
        this.storyService = storyService;
        this.vertx = vertx;
        this.appConfigService = appConfigService;
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
                                        pathStr != null ? pathStr : "/data/captures/"
                                ))
                        )
                );
    }

    private Uni<Bild> doUploadBild(MultipartFormDataInput input, UploadConfig config) {
        try {
            Map<String, Collection<FormValue>> formValues = input.getValues();
            // Konvertierung zu einer Map mit List statt Collection
            Map<String, List<FormValue>> formParts = new HashMap<>();

            formValues.forEach((key, collection) -> {
                formParts.put(key, new ArrayList<>(collection));
            });

            List<FormValue> fileParts = formParts.get("file");
            String title = getFormValue(formParts, "title");
            String description = getFormValue(formParts, "description");
            // Story-ID aus dem Formular extrahieren
            String storyIdStr = getFormValue(formParts, "storyId");
            Long storyId = null;
            if (storyIdStr != null && !storyIdStr.isEmpty()) {
                try {
                    storyId = Long.parseLong(storyIdStr);
                } catch (NumberFormatException e) {
                    log.error("Ungültige Story-ID: {}", storyIdStr);
                }
            }

            if (fileParts == null || fileParts.isEmpty()) {
                return Uni.createFrom().failure(
                        new WebApplicationException(
                                "Keine Datei gefunden",
                                Response.Status.BAD_REQUEST
                        )
                );
            }

            FormValue filePart = fileParts.get(0);
            String fileName = filePart.getFileName();
            String fileExtension = getFileExtension(fileName);

            // Überprüfung des Dateiformats
            if (!isAllowedFileType(fileExtension, config.allowedFileTypes)) {
                return Uni.createFrom().failure(
                        new WebApplicationException(
                                "Nicht unterstütztes Dateiformat. Erlaubte Formate: " + config.allowedFileTypes,
                                Response.Status.BAD_REQUEST
                        )
                );
            }

            // Überprüfung der Dateigröße
            long fileSize = filePart.getFileItem().getFileSize();
            if (fileSize > config.maxUploadSize) {
                return Uni.createFrom().failure(
                        new WebApplicationException(
                                "Datei zu groß. Maximale Größe: " + (config.maxUploadSize / 1024 / 1024) + "MB",
                                Response.Status.BAD_REQUEST
                        )
                );
            }

            // Eindeutigen Dateinamen generieren
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueFileName = timestamp + "_" + UUID.randomUUID().toString() + fileExtension;

            // Sicherstellen, dass das Zielverzeichnis existiert
            java.nio.file.Path dirPath = Paths.get(config.capturesPath);
            Files.createDirectories(dirPath);

            // Datei speichern
            java.nio.file.Path targetPath = dirPath.resolve(uniqueFileName);
            try (InputStream fileInputStream = filePart.getFileItem().getInputStream()) {
                Files.copy(fileInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            generateThumbnail(targetPath);

            // Bild-Entität erstellen und in der Datenbank speichern
            Bild bild = new Bild();
            bild.setPfad("/" + uniqueFileName);
            bild.setTitle(title);
            bild.setDescription(description);
            bild.setPriority(3);  // set default priority to 3 (green)

            // Story finden und zuweisen wenn vorhanden
            if (storyId != null) {
                // Hier muss ein Story-Service injiziert werden
                return storyService.findById(storyId)
                        .onItem().transformToUni(story -> {
                            if (story != null) {
                                bild.setStory(story);
                            }
                            return bildService.create(bild);
                        });
            }

            // hier Speicherung des Bildes in der Datenbank
            return bildService.create(bild);
        } catch (Exception e) {
            log.error("Fehler beim Upload: {}", e.getMessage(), e);
            return Uni.createFrom().failure(
                    new WebApplicationException(
                            "Fehler beim Upload: " + e.getMessage(),
                            Response.Status.INTERNAL_SERVER_ERROR
                    )
            );
        }
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

    public static String toThumbName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base + "_thumb.jpg";
    }

    private void generateThumbnail(java.nio.file.Path originalPath) throws Exception {
        java.nio.file.Path thumbPath = originalPath.getParent().resolve(toThumbName(originalPath.getFileName().toString()));
        Process process = new ProcessBuilder(
                "convert", originalPath.toString(), "-resize", "400x>", thumbPath.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Thumbnail-Generierung fehlgeschlagen (ImageMagick): " + output);
        }
    }

    private void rotateImageFile(java.nio.file.Path imagePath, int degrees) throws Exception {
        degrees = ((degrees % 360) + 360) % 360;
        if (degrees == 0) return;

        // ImageMagick statt javax.imageio — funktioniert zuverlässig im Native Image
        Process process = new ProcessBuilder(
                "convert", imagePath.toString(), "-rotate", String.valueOf(degrees), imagePath.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Bildrotation fehlgeschlagen (ImageMagick): " + output);
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