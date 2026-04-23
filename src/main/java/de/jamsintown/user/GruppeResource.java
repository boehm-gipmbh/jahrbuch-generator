package de.jamsintown.user;

import de.jamsintown.dtos.FotoboxConfigDTO;
import de.jamsintown.dtos.FotoboxTokenDTO;
import de.jamsintown.dtos.FotoboxTokenRequest;
import de.jamsintown.fotobox.FotoboxTokenService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/groups")
@RolesAllowed({"admin", "group-admin"})
public class GruppeResource {

    private final FotoboxTokenService fotoboxTokenService;
    private final UserService userService;

    @Inject
    public GruppeResource(FotoboxTokenService fotoboxTokenService, UserService userService) {
        this.fotoboxTokenService = fotoboxTokenService;
        this.userService = userService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<List<Gruppe>> list() {
        return Gruppe.listAll();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @WithTransaction
    public Uni<Gruppe> create(Gruppe gruppe) {
        return gruppe.persistAndFlush();
    }

    @POST
    @Path("{id}/fotobox-token")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<FotoboxTokenDTO> generateFotoboxToken(
            @PathParam("id") long groupId,
            FotoboxTokenRequest request) {
        return Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(NotFoundException::new)
                .map(gruppe -> new FotoboxTokenDTO(
                        fotoboxTokenService.generateToken(groupId, request.validFrom(), request.validTo())));
    }
}
