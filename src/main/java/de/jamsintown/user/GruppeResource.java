package de.jamsintown.user;

import de.jamsintown.dtos.FotoboxConfigDTO;
import de.jamsintown.dtos.FotoboxSetupRequest;
import de.jamsintown.dtos.FotoboxSetupResponse;
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
    private final GruppeService gruppeService;
    private final UserService userService;

    @Inject
    public GruppeResource(FotoboxTokenService fotoboxTokenService, GruppeService gruppeService, UserService userService) {
        this.fotoboxTokenService = fotoboxTokenService;
        this.gruppeService = gruppeService;
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

    /**
     * Erstellt eine neue Gruppe und generiert gleichzeitig den Fotobox-JWT.
     * Der Fotobox-User wird lazy beim ersten Capture angelegt.
     */
    @POST
    @Path("/fotobox-setup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<FotoboxSetupResponse> setupFotobox(FotoboxSetupRequest request) {
        return gruppeService.create(request.groupName())
                .map(gruppe -> new FotoboxSetupResponse(
                        gruppe.id,
                        gruppe.name,
                        fotoboxTokenService.generateToken(gruppe.id, request.validFrom(), request.validTo())));
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
