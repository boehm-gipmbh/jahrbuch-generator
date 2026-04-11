package de.jamsintown.auth;

import de.jamsintown.user.*;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.UUID;


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

  @PermitAll
  @POST
  @Path("/login")
  public Uni<String> login(AuthRequest request) {
    return authService.authenticate(request);
  }

  @PermitAll
  @GET
  @Path("/validate-token")
  public Uni<InvitationToken> validateToken(@QueryParam("token") UUID token) {
    return invitationTokenService.validate(token);
  }

  @PermitAll
  @POST
  @Path("/register")
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  public Uni<Void> register(@QueryParam("token") UUID token, RegisterRequest request) {
    return invitationTokenService.register(token, request.name(), request.email(), request.password())
      .replaceWithVoid();
  }

  @PermitAll
  @GET
  @Path("/verify-email")
  public Uni<Void> verifyEmail(@QueryParam("token") UUID token) {
    return emailVerificationService.verify(token);
  }

  /**
   * Schickt den Username per E-Mail. Gibt immer 200 zurück (kein Email-Enumeration).
   */
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

  /**
   * Startet den Passwort-Reset-Flow. Gibt immer 200 zurück (kein Email-Enumeration).
   */
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

  /**
   * Setzt das Passwort mit einem gültigen Reset-Token zurück.
   */
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

  /** Bestehender User tritt einer Gruppe bei (bei E-Mail-Kollision nach Login). */
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
          .replaceWithVoid();
      });
  }
}