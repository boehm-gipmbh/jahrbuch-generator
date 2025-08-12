package de.jamsintown.bild;

import de.jamsintown.capture.CaptureService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Path("/api/v1/bilder")
public class BilderUploadResource {

    private final BildService bildService;

    @Inject
    public BilderUploadResource(BildService bildService) {
        this.bildService = bildService;
    }

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    private String capturesPath;

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


            if (fileParts == null || fileParts.isEmpty()) {
              if (fileParts == null || fileParts.isEmpty()) {
                    return Uni.createFrom().failure(
                        new WebApplicationException(
                            "Keine Datei gefunden",
                            Response.Status.BAD_REQUEST
                        )
                    );
                }
            }

            FormValue filePart = fileParts.get(0);
            String fileName = filePart.getFileName();
            String fileExtension = getFileExtension(fileName);

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
            return bildService.create(bild);
            // Hier Speichern in der Datenbank...

//            return Response.status(Response.Status.CREATED)
//                    .entity(bild)
//                    .build();
        } catch (Exception e) {
         return Uni.createFrom().failure(
                new WebApplicationException(
                    "Fehler beim Upload: " + e.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR
                )
            );
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
}