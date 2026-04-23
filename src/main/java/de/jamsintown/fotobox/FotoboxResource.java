package de.jamsintown.fotobox;

import de.jamsintown.bild.Bild;
import de.jamsintown.capture.CaptureService;
import de.jamsintown.config.AppConfigService;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.dtos.FotoboxStateDTO;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Path("/api/v1/fotobox")
@PermitAll
public class FotoboxResource {

    @ConfigProperty(name = "jahrbuch.captures.station", defaultValue = "false")
    boolean capturesStation;

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String defaultCapturesPath;

    @ConfigProperty(name = "jahrbuch.fotobox.capture-user", defaultValue = "admin")
    String captureUser;

    private final CaptureService captureService;
    private final UserService userService;
    private final AppConfigService appConfigService;

    @Inject
    public FotoboxResource(CaptureService captureService, UserService userService, AppConfigService appConfigService) {
        this.captureService = captureService;
        this.userService = userService;
        this.appConfigService = appConfigService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<FotoboxStateDTO> getState() {
        boolean cameraConnected;
        String cameraModel = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("gphoto2", "--auto-detect");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            if (output.contains("usb:")) {
                cameraConnected = true;
                cameraModel = output.lines()
                        .filter(l -> l.contains("usb:"))
                        .findFirst()
                        .map(l -> l.replaceAll("  +.*", "").trim())
                        .orElse("Kamera");
            } else {
                cameraConnected = false;
            }
        } catch (Exception e) {
            log.debug("gphoto2 nicht verfügbar: {}", e.getMessage());
            cameraConnected = false;
        }

        boolean connected = cameraConnected;
        String model = cameraModel;

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return Bild.<Bild>listAll()
                .map(bilder -> bilder.stream()
                        .filter(b -> !b.deleted)
                        .filter(b -> b.created != null &&
                                b.created.toLocalDate().equals(today))
                        .map(b -> b.pfad)
                        .toList())
                .map(pfade -> new FotoboxStateDTO(capturesStation, connected, model, pfade));
    }

    @POST
    @Path("/capture")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<Bild> capture(ImageSettings imageSettings) {
        if (!capturesStation) {
            return Uni.createFrom().failure(new ForbiddenException("Capture Station nicht aktiv"));
        }
        return userService.findByName(captureUser)
                .chain(user -> captureService.createForUser(imageSettings, user));
    }

    @GET
    @Path("/bilder/{pfad: .+}")
    public Uni<Response> getBild(@PathParam("pfad") String pfad) {
        return appConfigService.getValue("jahrbuch.captures.path")
                .map(configPath -> configPath != null ? configPath : defaultCapturesPath)
                .map(capturesPath -> {
                    java.nio.file.Path basePath = Paths.get(capturesPath).normalize();
                    java.nio.file.Path filePath = basePath.resolve(pfad).normalize();
                    if (!filePath.startsWith(basePath)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }
                    File file = filePath.toFile();
                    if (!file.exists() || !file.isFile()) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                    String mimeType;
                    try {
                        mimeType = Files.probeContentType(filePath);
                    } catch (Exception e) {
                        mimeType = "image/jpeg";
                    }
                    if (mimeType == null) mimeType = "image/jpeg";
                    return Response.ok(file)
                            .header("Content-Type", mimeType)
                            .header("Cache-Control", "max-age=3600")
                            .build();
                });
    }
}
