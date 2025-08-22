package de.jamsintown.bild;

import de.jamsintown.capture.CaptureService;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.dtos.CaptureConfigDTO;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/bilder")
@RolesAllowed("user")
public class BildResource {

    @ConfigProperty
    (name = "jahrbuch.captures.station", defaultValue = "false")
    boolean capturesStation;
    private final BildService bildService;
    private final CaptureService captureService;

    @Inject
    public BildResource(BildService bildService, CaptureService captureService) {
        this.bildService = bildService;
        this.captureService = captureService;
    }

    @GET
    public Uni<List<Bild>> get() {
        return bildService.listForUser();
    }

    @GET
    @Path("/{id}")
    public Uni<Bild> getSingle(Long id) {
        return bildService.findById(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Bild> create(Bild bild) {
        return bildService.create(bild);
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Bild> update(@PathParam("id") Long id, Bild text) {
        text.id = id;
        return bildService.update(text);
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return bildService.delete(id);
    }

    @PUT
    @Path("/{id}/complete")
    public Uni<Boolean> setComplete(@PathParam("id") long id, boolean complete) {
        return bildService.setComplete(id, complete);
    }
    @POST
    @Path("/capture")
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Bild> create(ImageSettings imageSettings) {
        return captureService.create(imageSettings);
    }

    @GET
    @Path("/capture/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<CaptureConfigDTO> getCaptureConfig() {
        return Uni.createFrom().item(new CaptureConfigDTO(capturesStation));
    }
}
