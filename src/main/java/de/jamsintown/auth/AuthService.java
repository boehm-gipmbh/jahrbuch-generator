package de.jamsintown.auth;

import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.security.AuthenticationFailedException;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.HashSet;

@ApplicationScoped
public class AuthService {

  private final String issuer;
  private final UserService userService;

  @Inject
  public AuthService(
    @ConfigProperty(name = "mp.jwt.verify.issuer") String issuer, UserService userService) {
    this.issuer = issuer;
    this.userService = userService;
  }
    @WithSession
  public Uni<String> authenticate(AuthRequest authRequest) {
    Uni<de.jamsintown.user.User> lookup = authRequest.name().contains("@")
        ? userService.findByEmail(authRequest.name())
        : userService.findByName(authRequest.name());
    return lookup
      .onItem()
      .transform(user -> {
        if (user == null || !UserService.matches(user, authRequest.password())) {
          throw new AuthenticationFailedException("Invalid credentials");
        }
        if (!user.active) {
          throw new AuthenticationFailedException("Account deactivated");
        }
        if (!user.emailVerified) {
          throw new AuthenticationFailedException("Email not verified");
        }
        return generateToken(user);
      });
  }

  public String generateToken(de.jamsintown.user.User user) {
    return Jwt.issuer(issuer)
      .upn(user.name)
      .groups(new HashSet<>(user.roles))
      .expiresIn(Duration.ofHours(1L))
      .sign();
  }
}
