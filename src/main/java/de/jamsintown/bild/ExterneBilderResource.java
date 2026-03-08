package de.jamsintown.bild;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

@Path("/api/bilder/extern")
@RolesAllowed("user")
public class ExterneBilderResource {
    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    private String capturesPath;

    @GET
    @Path("/{pfad:.*}")
    public Response getBild(@PathParam("pfad") String pfad) {
        try {
            // Path-Traversal verhindern
            java.nio.file.Path basePath = Paths.get(capturesPath).normalize();
            java.nio.file.Path filePath = basePath.resolve(pfad).normalize();

            // Sicherstellen, dass die Datei innerhalb des erlaubten Verzeichnisses liegt
            if (!filePath.startsWith(basePath)) {
                return Response.status(Response.Status.FORBIDDEN).entity("Zugriff verweigert").build();
            }

            File file = filePath.toFile();
            if (!file.exists() || !file.isFile()) {
                return Response.status(Response.Status.NOT_FOUND).entity("Datei nicht gefunden").build();
            }

            // MIME-Typ ermitteln
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) {
                mimeType = MediaType.APPLICATION_OCTET_STREAM;
            }

            // Dateiname für Header URL-kodieren
            String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");

            return Response.ok(file)
                    .header("Content-Type", mimeType)
                    .header("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"")
                    .header("Cache-Control", "public, max-age=86400") // 1 Tag cachen
                    .build();
        } catch (Exception e) {
            return Response.serverError().entity("Fehler: " + e.getMessage()).build();
        }
    }
}