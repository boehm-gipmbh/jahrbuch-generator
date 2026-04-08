package de.jamsintown.auth;

import de.jamsintown.user.*;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.UUID;


@Path("/api/v1/auth")
public class AuthResource {

  private final AuthService authService;
  private final InvitationTokenService invitationTokenService;
  private final EmailVerificationService emailVerificationService;
  private final GruppeService gruppeService;
  private final UserService userService;

  @Inject
  public AuthResource(AuthService authService, InvitationTokenService invitationTokenService,
                      EmailVerificationService emailVerificationService,
                      GruppeService gruppeService, UserService userService) {
    this.authService = authService;
    this.invitationTokenService = invitationTokenService;
    this.emailVerificationService = emailVerificationService;
    this.gruppeService = gruppeService;
    this.userService = userService;
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

  /** Bestehender User tritt einer Gruppe bei (bei E-Mail-Kollision nach Login). */
  @RolesAllowed("user")
  @POST
  @Path("/join-group")
  public Uni<Void> joinGroup(@QueryParam("token") UUID token) {
    return invitationTokenService.validate(token)
      .chain(invitation -> {
        if (invitation.group == null) return Uni.createFrom().voidItem();
        return userService.getCurrentUser()
          .chain(user -> gruppeService.addToGroup(user, invitation.group))
          .replaceWithVoid();
      });
  }
}