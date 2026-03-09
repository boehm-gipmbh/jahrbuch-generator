package de.jamsintown.bild;

import io.quarkus.security.ForbiddenException;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.ObjectNotFoundException;

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

    private final BildService bildService;

    @Inject
    public ExterneBilderResource(BildService bildService) {
        this.bildService = bildService;
    }

    @GET
    @Path("/{pfad:.*}")
    public Uni<Response> getBild(@PathParam("pfad") String pfad) {
        // Path-Traversal verhindern
        java.nio.file.Path basePath = Paths.get(capturesPath).normalize();
        java.nio.file.Path filePath = basePath.resolve(pfad).normalize();

        if (!filePath.startsWith(basePath)) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.FORBIDDEN).entity("Zugriff verweigert").build());
        }

        // Eigentümerschaft prüfen: Bild muss dem authentifizierten User gehören
        return bildService.findByPfad("/" + pfad)
                .chain(bild -> {
                    File file = filePath.toFile();
                    if (!file.exists() || !file.isFile()) {
                        return Uni.createFrom().item(
                                Response.status(Response.Status.NOT_FOUND).entity("Datei nicht gefunden").build());
                    }
                    try {
                        String mimeType = Files.probeContentType(filePath);
                        if (mimeType == null) {
                            mimeType = MediaType.APPLICATION_OCTET_STREAM;
                        }
                        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8.toString())
                                .replace("+", "%20");
                        return Uni.createFrom().item(Response.ok(file)
                                .header("Content-Type", mimeType)
                                .header("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"")
                                .header("Cache-Control", "public, max-age=86400")
                                .build());
                    } catch (Exception e) {
                        return Uni.createFrom().item(
                                Response.serverError().entity("Fehler: " + e.getMessage()).build());
                    }
                })
                .onFailure(e -> e instanceof ObjectNotFoundException || e instanceof ForbiddenException)
                .recoverWithItem(Response.status(Response.Status.FORBIDDEN).entity("Zugriff verweigert").build());
    }
}
