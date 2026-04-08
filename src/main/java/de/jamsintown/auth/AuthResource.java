package de.jamsintown.auth;

import de.jamsintown.user.EmailVerificationService;
import de.jamsintown.user.InvitationToken;
import de.jamsintown.user.InvitationTokenService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
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

  @Inject
  public AuthResource(AuthService authService, InvitationTokenService invitationTokenService,
                      EmailVerificationService emailVerificationService) {
    this.authService = authService;
    this.invitationTokenService = invitationTokenService;
    this.emailVerificationService = emailVerificationService;
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
}