package de.jamsintown.bild;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
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
        return bildService.findById(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Bild> create(Bild bild) {
        return bildService.create(bild);
    }

    @PUT
    @Path("/{id}/protect")
    public Uni<Boolean> setProtect(@PathParam("id") long id, boolean protect) {
        return bildService.setProtect(id, protect);
    }
}
