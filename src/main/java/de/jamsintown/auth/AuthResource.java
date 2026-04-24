package de.jamsintown.auth;

import de.jamsintown.user.*;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.UUID;


@Tag(name = "Auth", description = "Login, Registrierung, E-Mail-Verifizierung, Passwort-Reset, Gruppenbeitritt")
@Path("/api/v1/auth")
public class AuthResource {

  private final AuthService authService;
  private final InvitationTokenService invitationTokenService;
  private final EmailVerificationService emailVerificationService;
  private final GruppeService gruppeService;
  private final UserService userService;
  private final PasswordResetService passwordResetService;
  private final UsernameReminderEmailService usernameReminderEmailService;

  @Inject
  public AuthResource(AuthService authService, InvitationTokenService invitationTokenService,
                      EmailVerificationService emailVerificationService,
                      GruppeService gruppeService, UserService userService,
                      PasswordResetService passwordResetService,
                      UsernameReminderEmailService usernameReminderEmailService) {
    this.authService = authService;
    this.invitationTokenService = invitationTokenService;
    this.emailVerificationService = emailVerificationService;
    this.gruppeService = gruppeService;
    this.userService = userService;
    this.passwordResetService = passwordResetService;
    this.usernameReminderEmailService = usernameReminderEmailService;
  }

  @Operation(summary = "Login", description = "Gibt bei Erfolg ein JWT zurück")
  @APIResponse(responseCode = "200", description = "JWT-Token als String")
  @APIResponse(responseCode = "401", description = "Falsche Zugangsdaten")
  @PermitAll
  @POST
  @Path("/login")
  public Uni<String> login(AuthRequest request) {
    return authService.authenticate(request);
  }

  @Operation(summary = "Einladungstoken prüfen")
  @APIResponse(responseCode = "200", description = "Token gültig")
  @APIResponse(responseCode = "410", description = "Token ungültig oder abgelaufen")
  @PermitAll
  @GET
  @Path("/validate-token")
  public Uni<InvitationToken> validateToken(@QueryParam("token") UUID token) {
    return invitationTokenService.validate(token);
  }

  @Operation(summary = "Neuen User registrieren")
  @APIResponse(responseCode = "201", description = "Registrierung erfolgreich")
  @APIResponse(responseCode = "400", description = "Validierungsfehler (Name, Passwort)")
  @APIResponse(responseCode = "409", description = "Username oder E-Mail bereits vergeben")
  @APIResponse(responseCode = "410", description = "Token ungültig oder abgelaufen")
  @PermitAll
  @POST
  @Path("/register")
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  public Uni<Void> register(@QueryParam("token") UUID token, RegisterRequest request) {
    return invitationTokenService.register(token, request.name(), request.email(), request.password())
      .replaceWithVoid();
  }

  @Operation(summary = "E-Mail-Adresse bestätigen")
  @APIResponse(responseCode = "200", description = "E-Mail erfolgreich bestätigt")
  @APIResponse(responseCode = "410", description = "Token ungültig oder abgelaufen")
  @PermitAll
  @GET
  @Path("/verify-email")
  public Uni<Void> verifyEmail(@QueryParam("token") UUID token) {
    return emailVerificationService.verify(token);
  }

  @Operation(summary = "Username per E-Mail senden", description = "Gibt immer 200 zurück (kein E-Mail-Enumeration)")
  @APIResponse(responseCode = "200", description = "E-Mail versandt (oder User nicht gefunden — kein Unterschied)")
  @PermitAll
  @POST
  @Path("/forgot-username")
  @Consumes(MediaType.APPLICATION_JSON)
  public Uni<Response> forgotUsername(ForgotUsernameRequest request) {
    if (request == null || request.email() == null || request.email().isBlank()) {
      return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
    }
    return userService.findByEmail(request.email())
        .map(user -> {
          if (user != null) {
            usernameReminderEmailService.sendUsernameReminder(user.email, user.name);
          }
          return Response.ok().build();
        });
  }

  @Operation(summary = "Passwort-Reset anfordern", description = "Gibt immer 200 zurück (kein E-Mail-Enumeration)")
  @APIResponse(responseCode = "200", description = "Reset-E-Mail versandt (oder User nicht gefunden — kein Unterschied)")
  @PermitAll
  @POST
  @Path("/forgot-password")
  @Consumes(MediaType.APPLICATION_JSON)
  public Uni<Response> forgotPassword(ForgotPasswordRequest request) {
    if (request == null || request.email() == null || request.email().isBlank()) {
      return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
    }
    return passwordResetService.requestReset(request.email())
        .map(v -> Response.ok().build());
  }

  @Operation(summary = "Passwort zurücksetzen")
  @APIResponse(responseCode = "204", description = "Passwort erfolgreich geändert")
  @APIResponse(responseCode = "400", description = "Token fehlt oder Passwort ungültig")
  @APIResponse(responseCode = "410", description = "Reset-Token ungültig oder abgelaufen")
  @PermitAll
  @POST
  @Path("/reset-password")
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(204)
  public Uni<Void> resetPassword(@QueryParam("token") UUID token, ResetPasswordRequest request) {
    if (token == null) {
      throw new BadRequestException("token query parameter is required");
    }
    if (request == null || request.password() == null || request.password().isBlank()) {
      throw new BadRequestException("password is required");
    }
    return passwordResetService.resetPassword(token, request.password());
  }

  @Operation(summary = "Gruppe beitreten", description = "Bestehender User tritt via Einladungstoken einer Gruppe bei")
  @APIResponse(responseCode = "204", description = "Gruppe erfolgreich beigetreten (oder bereits Mitglied)")
  @APIResponse(responseCode = "404", description = "Token hat keine zugeordnete Gruppe")
  @APIResponse(responseCode = "410", description = "Token ungültig oder abgelaufen")
  @RolesAllowed("user")
  @POST
  @Path("/join-group")
  @io.quarkus.hibernate.reactive.panache.common.WithTransaction
  public Uni<Void> joinGroup(@QueryParam("token") UUID token) {
    return invitationTokenService.validate(token)
      .chain(invitation -> {
        if (invitation.group == null) {
          throw new ClientErrorException("Einladungslink hat keine Gruppe", Response.Status.NOT_FOUND);
        }
        return userService.getCurrentUser()
          .chain(user -> gruppeService.addToGroup(user, invitation.group))
          .onFailure(AuthResource::isUserGroupsDuplicate).recoverWithNull()
          .replaceWithVoid();
      });
  }

  private static boolean isUserGroupsDuplicate(Throwable t) {
    while (t != null) {
      if (t instanceof io.vertx.pgclient.PgException pg
          && "23505".equals(pg.getSqlState())
          && pg.getMessage().contains("user_groups_pkey")) return true;
      t = t.getCause();
    }
    return false;
  }
}