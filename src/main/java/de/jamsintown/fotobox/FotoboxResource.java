package de.jamsintown.fotobox;

import de.jamsintown.bild.Bild;
import de.jamsintown.capture.CaptureService;
import de.jamsintown.config.AppConfigService;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.dtos.FotoboxConfigDTO;
import de.jamsintown.dtos.FotoboxStateDTO;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.GruppeService;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Path("/api/v1/fotobox")
@PermitAll
public class FotoboxResource {

    @ConfigProperty(name = "jahrbuch.captures.station", defaultValue = "false")
    boolean capturesStation;

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String defaultCapturesPath;

    @ConfigProperty(name = "jahrbuch.fotobox.capture-user", defaultValue = "fotobox")
    String captureUser;

    @ConfigProperty(name = "jahrbuch.fotobox.token")
    Optional<String> fotoboxToken;

    private final CaptureService captureService;
    private final UserService userService;
    private final GruppeService gruppeService;
    private final AppConfigService appConfigService;
    private final JsonWebToken jwt;

    @Inject
    public FotoboxResource(CaptureService captureService, UserService userService,
                           GruppeService gruppeService, AppConfigService appConfigService,
                           JsonWebToken jwt) {
        this.captureService = captureService;
        this.userService = userService;
        this.gruppeService = gruppeService;
        this.appConfigService = appConfigService;
        this.jwt = jwt;
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
        return de.jamsintown.bild.Bild.<de.jamsintown.bild.Bild>listAll()
                .map(bilder -> bilder.stream()
                        .filter(b -> !b.deleted)
                        .filter(b -> b.created != null &&
                                b.created.toLocalDate().equals(today))
                        .map(b -> b.pfad)
                        .toList())
                .map(pfade -> new FotoboxStateDTO(capturesStation, connected, model, pfade));
    }

    @GET
    @Path("/station-token")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStationToken() {
        return fotoboxToken
                .map(t -> Response.ok("{\"token\":\"" + t + "\"}").build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    private Long extractGroupId() {
        Object raw = jwt.getClaim("group_id");
        if (raw == null) return null;
        if (raw instanceof Number) return ((Number) raw).longValue();
        try { return Long.parseLong(raw.toString()); } catch (NumberFormatException e) { return null; }
    }

    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("fotobox")
    @WithSession
    public Uni<FotoboxConfigDTO> getConfig() {
        Long groupId = extractGroupId();
        if (groupId == null) {
            return Uni.createFrom().failure(new ForbiddenException("Kein group_id im Token"));
        }
        return Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Gruppe nicht gefunden"))
                .map(gruppe -> new FotoboxConfigDTO(gruppe.id, gruppe.name, "Small Fine JPEG"));
    }

    @POST
    @Path("/capture")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("fotobox")
    @WithSession
    public Uni<Bild> capture(ImageSettings imageSettings) {
        Long groupId = extractGroupId();
        if (groupId == null) {
            return Uni.createFrom().failure(new ForbiddenException("Kein group_id im Token"));
        }
        return Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Gruppe nicht gefunden"))
                .chain(gruppe -> userService.findByName(captureUser)
                        .onItem().ifNull().switchTo(() -> {
                            log.info("Fotobox-User '{}' nicht gefunden — wird angelegt", captureUser);
                            return userService.createFotoboxUser(captureUser);
                        })
                        .chain(user -> gruppeService.addToGroup(user, gruppe)
                                .chain(updatedUser -> captureService.createForUser(imageSettings, updatedUser, gruppe))));
    }

    @GET
    @Path("/bilder/{pfad: .+}")
    public Response getBild(@PathParam("pfad") String pfad) {
        java.nio.file.Path basePath = Paths.get(defaultCapturesPath).normalize();
        java.nio.file.Path filePath = basePath.resolve(pfad).normalize();
        if (!filePath.startsWith(basePath)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            log.warn("Datei nicht gefunden: {}", filePath);
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
    }
}
