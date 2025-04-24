package de.jamsintown.capture;

import de.jamsintown.bild.Bild;
import de.jamsintown.config.main.ImageSettings;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/api/v1/capture")
public class CaptureResource {
    private final CaptureService captureService;

    public CaptureResource(CaptureService captureService) {
        this.captureService = captureService;
    }

    // Define your REST endpoints here
    // For example:
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Bild> create(ImageSettings imageSettings) {
        return captureService.create(imageSettings);
    }



}
