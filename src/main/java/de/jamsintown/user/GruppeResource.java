package de.jamsintown.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.jamsintown.dtos.FotoboxSetupRequest;
import de.jamsintown.dtos.FotoboxSetupResponse;
import de.jamsintown.dtos.FotoboxTokenDTO;
import de.jamsintown.dtos.FotoboxTokenRequest;
import de.jamsintown.fotobox.FotoboxTokenEmailService;
import de.jamsintown.fotobox.FotoboxTokenService;
import de.jamsintown.pdf.PdfSettings;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.UnauthorizedException;
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
    private final ObjectMapper objectMapper;

    @Inject
    public GruppeResource(FotoboxTokenService fotoboxTokenService,
                          FotoboxTokenEmailService fotoboxTokenEmailService,
                          GruppeService gruppeService,
                          UserService userService,
                          ObjectMapper objectMapper) {
        this.fotoboxTokenService = fotoboxTokenService;
        this.fotoboxTokenEmailService = fotoboxTokenEmailService;
        this.gruppeService = gruppeService;
        this.userService = userService;
        this.objectMapper = objectMapper;
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

    @GET
    @Path("{id}/pdf-config")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"admin", "group-admin"})
    @WithSession
    public Uni<PdfSettings> getPdfConfig(@PathParam("id") long groupId) {
        return userService.getCurrentUser()
            .chain(user -> Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(NotFoundException::new)
                .map(gruppe -> {
                    assertManagerOrAdmin(user, groupId);
                    if (gruppe.pdfSettings == null) return PdfSettings.defaults();
                    try {
                        return objectMapper.readValue(gruppe.pdfSettings, PdfSettings.class);
                    } catch (Exception e) {
                        return PdfSettings.defaults();
                    }
                }));
    }

    @PUT
    @Path("{id}/pdf-config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"admin", "group-admin"})
    @WithTransaction
    public Uni<PdfSettings> putPdfConfig(@PathParam("id") long groupId, PdfSettings settings) {
        return userService.getCurrentUser()
            .chain(user -> Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(NotFoundException::new)
                .chain(gruppe -> {
                    assertManagerOrAdmin(user, groupId);
                    try {
                        gruppe.pdfSettings = objectMapper.writeValueAsString(settings);
                    } catch (Exception e) {
                        throw new RuntimeException("PDF-Einstellungen konnten nicht gespeichert werden", e);
                    }
                    return gruppe.<Gruppe>persistAndFlush().map(g -> settings);
                }));
    }

    private void assertManagerOrAdmin(User user, long groupId) {
        boolean isAdmin = user.roles != null && user.roles.contains("admin");
        boolean isManager = (user.managedGroup != null && groupId == user.managedGroup.id)
            || (user.managedGroups != null && user.managedGroups.stream().anyMatch(g -> groupId == g.id));
        if (!isAdmin && !isManager) {
            throw new UnauthorizedException("Kein Zugriff auf Gruppe: " + groupId);
        }
    }
}
