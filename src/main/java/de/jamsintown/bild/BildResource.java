package de.jamsintown.bild;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/bilder")
@RolesAllowed("user")
public class BildResource {
    private final BildService bildService;

    @Inject
    public BildResource(BildService bildService) {
        this.bildService = bildService;
    }

    @GET
    public Uni<List<Bild>> get() {
        return bildService.listForUser();
    }

    @GET
    @Path("/{id}")
    public Uni<Bild> getSingle(Long id) {
        return bildService.getIdForUser(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Bild> create(Bild bild) {
        return bildService.create(bild);
    }
}
