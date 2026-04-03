package de.jamsintown.auth;

import de.jamsintown.user.InvitationToken;
import de.jamsintown.user.InvitationTokenService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;


@Path("/api/v1/auth")
public class AuthResource {

  private final AuthService authService;
  private final InvitationTokenService invitationTokenService;

  @Inject
  public AuthResource(AuthService authService, InvitationTokenService invitationTokenService) {
    this.authService = authService;
    this.invitationTokenService = invitationTokenService;
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
  public Uni<String> register(@QueryParam("token") UUID token, RegisterRequest request) {
    return invitationTokenService.register(token, request.name(), request.email(), request.password())
      .map(user -> authService.generateToken(user));
  }
}