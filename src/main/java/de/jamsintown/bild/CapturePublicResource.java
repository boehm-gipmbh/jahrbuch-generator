package de.jamsintown.bild;

import de.jamsintown.dtos.CameraStatusDTO;
import de.jamsintown.dtos.CaptureConfigDTO;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@Path("/api/v1/bilder")
@PermitAll
public class CapturePublicResource {

    @ConfigProperty(name = "jahrbuch.captures.station", defaultValue = "false")
    boolean capturesStation;

    @GET
    @Path("/capture/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<CaptureConfigDTO> getCaptureConfig() {
        return Uni.createFrom().item(new CaptureConfigDTO(capturesStation));
    }

    @GET
    @Path("/capture/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<CameraStatusDTO> getCameraStatus() {
        return Uni.createFrom().item(() -> {
            try {
                Process process = new ProcessBuilder("gphoto2", "--auto-detect").start();
                String output = new String(process.getInputStream().readAllBytes());
                process.waitFor();
                java.util.Optional<String> cameraLine = output.lines()
                        .filter(l -> !l.startsWith("Modell") && !l.startsWith("Model")
                                && !l.startsWith("-") && !l.isBlank())
                        .findFirst();
                if (cameraLine.isPresent()) {
                    String model = cameraLine.get().replaceAll("  +.*", "").trim();
                    return new CameraStatusDTO(true, model);
                }
            } catch (Exception e) {
                log.debug("gphoto2 --auto-detect fehlgeschlagen: {}", e.getMessage());
            }
            return new CameraStatusDTO(false, null);
        });
    }
}
