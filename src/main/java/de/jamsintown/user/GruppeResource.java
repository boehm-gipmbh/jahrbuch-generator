package de.jamsintown.user;

import de.jamsintown.dtos.FotoboxSetupRequest;
import de.jamsintown.dtos.FotoboxSetupResponse;
import de.jamsintown.dtos.FotoboxTokenDTO;
import de.jamsintown.dtos.FotoboxTokenRequest;
import de.jamsintown.fotobox.FotoboxTokenEmailService;
import de.jamsintown.fotobox.FotoboxTokenService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/groups")
@RolesAllowed("admin")
public class GruppeResource {

    private final FotoboxTokenService fotoboxTokenService;
    private final FotoboxTokenEmailService fotoboxTokenEmailService;
    private final GruppeService gruppeService;
    private final UserService userService;

    @Inject
    public GruppeResource(FotoboxTokenService fotoboxTokenService,
                          FotoboxTokenEmailService fotoboxTokenEmailService,
                          GruppeService gruppeService,
                          UserService userService) {
        this.fotoboxTokenService = fotoboxTokenService;
        this.fotoboxTokenEmailService = fotoboxTokenEmailService;
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

    @POST
    @Path("/fotobox-setup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @WithTransaction
    public Uni<FotoboxSetupResponse> setupFotobox(FotoboxSetupRequest request) {
        return userService.getCurrentUser()
                .chain(admin -> gruppeService.create(request.groupName())
                        .chain(gruppe -> {
                            InvitationToken invToken = new InvitationToken();
                            invToken.token = UUID.randomUUID();
                            invToken.label = gruppe.name;
                            invToken.role = "user";
                            invToken.expiresAt = request.validTo().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).toZonedDateTime();
                            invToken.group = gruppe;
                            invToken.active = true;
                            invToken.createdBy = admin;
                            return invToken.<InvitationToken>persistAndFlush()
                                    .chain(saved -> fotoboxTokenService.generateToken(gruppe.id, request.validFrom(), request.validTo()))
                                    .map(token -> new FotoboxSetupResponse(gruppe.id, gruppe.name, token))
                                    .invoke(response -> {
                                        if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
                                            fotoboxTokenEmailService.sendTokenMail(
                                                    request.recipientEmail(),
                                                    gruppe.name,
                                                    response.token(),
                                                    request.validTo().toString());
                                        }
                                    });
                        }));
    }

    @POST
    @Path("{id}/fotobox-token")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<FotoboxTokenDTO> generateFotoboxToken(
            @PathParam("id") long groupId,
            FotoboxTokenRequest request) {
        return Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(NotFoundException::new)
                .chain(gruppe -> fotoboxTokenService.generateToken(groupId, request.validFrom(), request.validTo())
                        .map(token -> new FotoboxTokenDTO(token, gruppe.name)))
                .invoke(dto -> {
                    if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
                        fotoboxTokenEmailService.sendTokenMail(
                                request.recipientEmail(),
                                dto.groupName(),
                                dto.token(),
                                request.validTo().toString());
                    }
                });
    }

    @DELETE
    @Path("{id}/fotobox-token")
    @WithTransaction
    public Uni<Void> revokeFotoboxToken(@PathParam("id") long groupId) {
        return Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(NotFoundException::new)
                .chain(gruppe -> fotoboxTokenService.revokeToken(groupId));
    }
}
