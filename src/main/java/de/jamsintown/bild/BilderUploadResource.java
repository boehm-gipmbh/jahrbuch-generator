package de.jamsintown.bild;

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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
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

    @Inject
    public BilderUploadResource(BildService bildService, StoryService storyService) {
        this.bildService = bildService;
        this.storyService = storyService;
    }

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    private String capturesPath;

    @ConfigProperty(name = "jahrbuch.upload.max-size", defaultValue = "5242880") // 5MB Standardwert
    private long maxUploadSize;

    @ConfigProperty(name = "jahrbuch.upload.allowed-types", defaultValue = ".jpg,.jpeg,.png,.gif")
    private String allowedFileTypes;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Bild> uploadBild(MultipartFormDataInput input) {
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
            if (!isAllowedFileType(fileExtension)) {
                return Uni.createFrom().failure(
                        new WebApplicationException(
                                "Nicht unterstütztes Dateiformat. Erlaubte Formate: " + allowedFileTypes,
                                Response.Status.BAD_REQUEST
                        )
                );
            }

            // Überprüfung der Dateigröße
            long fileSize = filePart.getFileItem().getFileSize();
            if (fileSize > maxUploadSize) {
                return Uni.createFrom().failure(
                        new WebApplicationException(
                                "Datei zu groß. Maximale Größe: " + (maxUploadSize / 1024 / 1024) + "MB",
                                Response.Status.BAD_REQUEST
                        )
                );
            }

            // Eindeutigen Dateinamen generieren
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueFileName = timestamp + "_" + UUID.randomUUID().toString() + fileExtension;

            // Sicherstellen, dass das Zielverzeichnis existiert
            java.nio.file.Path dirPath = Paths.get(capturesPath);
            Files.createDirectories(dirPath);

            // Datei speichern
            java.nio.file.Path targetPath = dirPath.resolve(uniqueFileName);
            try (InputStream fileInputStream = filePart.getFileItem().getInputStream()) {
                Files.copy(fileInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

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
    public UploadConfigDTO getUploadConfig() {
        // Konvertiere String mit erlaubten Dateitypen in eine Liste
        List<String> allowedTypesList = Arrays.asList(allowedFileTypes.split(","));

        return new UploadConfigDTO(maxUploadSize, allowedTypesList);
    }

    /**
     * Einfacher Datei-Upload-Endpunkt, der eine Datei entgegennimmt und im Verzeichnis /data/captures speichert.
     * Der Dateiname wird beibehalten.
     */
    @POST
    @Path("/uploadcapture")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(MultipartFormDataInput input) {
        try {
            Map<String, Collection<FormValue>> formValues = input.getValues();
            List<FormValue> fileParts = new ArrayList<>(formValues.get("file"));
            if (fileParts.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Keine Datei gefunden").build();
            }
            FormValue filePart = fileParts.get(0);
            String fileName = filePart.getFileName();
            try (InputStream fileInputStream = filePart.getFileItem().getInputStream()) {
                java.nio.file.Path target = java.nio.file.Paths.get("/data/captures", fileName);
                java.nio.file.Files.copy(fileInputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return Response.ok("File uploaded").build();
        } catch (Exception e) {
            return Response.serverError().entity("Upload failed: " + e.getMessage()).build();
        }
    }



    @POST
    @Path("/{id}/rotate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Bild> rotateBild(@PathParam("id") Long id, RotationDTO rotation) {
        return bildService.findById(id)
                .onItem().transformToUni(bild -> {
                    if (bild == null) {
                        return Uni.createFrom().failure(
                                new WebApplicationException("Bild nicht gefunden", 404)
                        );
                    }

                    // Pfad aus der Bild-Entität holen
                    String bildPfad = bild.getPfad();

                    // Vollständigen Dateipfad erstellen
                    java.nio.file.Path imagePath = Paths.get(capturesPath).resolve(bildPfad.substring(1));

                    // Bild rotieren
                    try {
                        // Bild einlesen
                        BufferedImage originalImage = ImageIO.read(imagePath.toFile());

                        // Neue Bildgröße bestimmen
                        int degrees = rotation.getDegrees();
                        // Winkel normalisieren (auf 0-360 Grad)
                        degrees = ((degrees % 360) + 360) % 360;
                        int width = originalImage.getWidth();
                        int height = originalImage.getHeight();

                        BufferedImage rotatedImage;

                        // Bildrotation durchführen
                        if (degrees == 90 || degrees == 270) {
                            rotatedImage = new BufferedImage(height, width, originalImage.getType());
                        } else {
                            rotatedImage = new BufferedImage(width, height, originalImage.getType());
                        }

                        Graphics2D g2d = rotatedImage.createGraphics();
                        AffineTransform transform = new AffineTransform();

                        // Transformation für die Rotation
                        if (degrees == 90) {
                            transform.translate(height, 0);
                            transform.rotate(Math.toRadians(90));
                        } else if (degrees == 180) {
                            transform.translate(width, height);
                            transform.rotate(Math.toRadians(180));
                        } else if (degrees == 270) {
                            transform.translate(0, width);
                            transform.rotate(Math.toRadians(270));
                        }

                        g2d.drawImage(originalImage, transform, null);
                        g2d.dispose();

                        // Dateiformat aus dem Dateinamen extrahieren
                        String format = getFileFormat(imagePath.getFileName().toString());

                        // Rotiertes Bild speichern
                        ImageIO.write(rotatedImage, format, imagePath.toFile());

                        return bildService.update(bild);
                    } catch (Exception e) {
                        return Uni.createFrom().failure(
                                new WebApplicationException("Fehler beim Rotieren: " + e.getMessage(),
                                        Response.Status.INTERNAL_SERVER_ERROR)
                        );
                    }
                });
    }

    // Hilfsmethode zum Extrahieren des Dateiformats
    private String getFileFormat(String fileName) {
        String extension = getFileExtension(fileName);
        // Entferne den Punkt vom Dateiformat
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        return extension;
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

    private boolean isAllowedFileType(String fileExtension) {
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
}