package de.jamsintown.fotobox;

import de.jamsintown.capture.CaptureService;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.dtos.FotoboxStateDTO;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Path("/api/v1/fotobox")
@PermitAll
public class FotoboxResource {

    @ConfigProperty(name = "jahrbuch.captures.station", defaultValue = "false")
    boolean capturesStation;

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String capturesPath;

    @ConfigProperty(name = "jahrbuch.fotobox.capture-user", defaultValue = "fotobox")
    String captureUser;

    @ConfigProperty(name = "jahrbuch.fotobox.token")
    Optional<String> fotoboxToken;

    @ConfigProperty(name = "jahrbuch.app.url", defaultValue = "https://jahrbuch-generator.fly.dev")
    String appUrl;

    private final CaptureService captureService;
    private final FotoboxDbService fotoboxDbService;
    private final FotoboxTokenService fotoboxTokenService;
    private final JsonWebToken jwt;

    @Inject
    public FotoboxResource(CaptureService captureService, FotoboxDbService fotoboxDbService,
                           FotoboxTokenService fotoboxTokenService, JsonWebToken jwt) {
        this.captureService = captureService;
        this.fotoboxDbService = fotoboxDbService;
        this.fotoboxTokenService = fotoboxTokenService;
        this.jwt = jwt;
    }

    @GET
    @Blocking
    @Produces(MediaType.APPLICATION_JSON)
    public FotoboxStateDTO getState() {
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

        List<String> todaysPfade = listLocalCapturesForToday();
        return new FotoboxStateDTO(capturesStation, cameraConnected, cameraModel, todaysPfade);
    }

    private List<String> listLocalCapturesForToday() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        try {
            java.nio.file.Path base = Paths.get(capturesPath);
            if (!Files.exists(base)) return List.of();
            try (var stream = Files.walk(base, 3)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            try {
                                var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
                                return attrs.creationTime().toInstant()
                                        .atZone(ZoneId.systemDefault()).toLocalDate().equals(today);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .map(p -> "/" + base.relativize(p))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Fehler beim Lesen der lokalen Captures: {}", e.getMessage());
            return List.of();
        }
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

    private void validateJti(long groupId) {
        String jti = jwt.getTokenID();
        if (jti == null) throw new ForbiddenException("Kein jti im Token");
        boolean valid = fotoboxTokenService.isValid(groupId, jti).await().indefinitely();
        if (!valid) throw new ForbiddenException("Token wurde widerrufen oder ist ungültig");
    }

    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("fotobox")
    @Blocking
    public Response getConfig() {
        Long groupId = extractGroupId();
        if (groupId == null) throw new ForbiddenException("Kein group_id im Token");
        validateJti(groupId);

        if (capturesStation) {
            return proxyGetToFlyio("/api/v1/fotobox/config");
        }
        return Response.ok(fotoboxDbService.getConfig(groupId).await().indefinitely()).build();
    }

    @POST
    @Path("/capture")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("fotobox")
    @Blocking
    public Response capture(ImageSettings imageSettings) {
        if (!capturesStation) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).build();
        }
        Long groupId = extractGroupId();
        if (groupId == null) return Response.status(Response.Status.FORBIDDEN).build();
        validateJti(groupId);
        String token = fotoboxToken.orElse(null);
        if (token == null) return Response.status(Response.Status.FORBIDDEN).build();

        try {
            java.nio.file.Path capturedFile = captureService.captureToLocalFile(imageSettings, groupId, capturesPath);
            return uploadToFlyio(capturedFile, token);
        } catch (Exception e) {
            log.error("Capture fehlgeschlagen: {}", e.getMessage(), e);
            return Response.serverError().entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    // Empfängt Bild-Upload von der Capture-Station (läuft auf Fly.io mit DB-Zugriff)
    @POST
    @Path("/upload")
    @Consumes("image/jpeg")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("fotobox")
    @Blocking
    public Response upload(
            @HeaderParam("X-Filename") String filename,
            byte[] imageBytes) {
        if (capturesStation) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).build();
        }
        Long groupId = extractGroupId();
        if (groupId == null) return Response.status(Response.Status.BAD_REQUEST).build();
        validateJti(groupId);

        String fn = filename != null ? filename : "capture_" + System.currentTimeMillis() + ".jpg";
        return Response.ok(fotoboxDbService.saveBildForGroup(imageBytes, fn, groupId, capturesPath, captureUser)
                .await().indefinitely()).build();
    }

    private Response proxyGetToFlyio(String path) {
        String token = fotoboxToken.orElse(null);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(appUrl + path))
                    .GET();
            if (token != null) req.header("Authorization", "Bearer " + token);
            HttpResponse<String> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            return Response.status(resp.statusCode()).type(MediaType.APPLICATION_JSON).entity(resp.body()).build();
        } catch (Exception e) {
            log.error("Proxy zu Fly.io fehlgeschlagen: {}", e.getMessage());
            return Response.serverError().build();
        }
    }

    private Response uploadToFlyio(java.nio.file.Path file, String token) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file);
        Long groupId = extractGroupId();

        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appUrl + "/api/v1/fotobox/upload"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "image/jpeg")
                .header("X-Filename", file.getFileName().toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Upload fehlgeschlagen: HTTP " + response.statusCode());
        }
        return Response.ok(response.body()).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/bilder/{pfad: .+}")
    public Response getBild(@PathParam("pfad") String pfad) {
        java.nio.file.Path basePath = Paths.get(capturesPath).normalize();
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
