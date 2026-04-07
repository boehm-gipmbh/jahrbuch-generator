package de.jamsintown.user;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class InvitationTokenService {

  private final JsonWebToken jwt;
  private final EmailVerificationService emailVerificationService;

  @Inject
  public InvitationTokenService(JsonWebToken jwt, EmailVerificationService emailVerificationService) {
    this.jwt = jwt;
    this.emailVerificationService = emailVerificationService;
  }

  @WithSession
  public Uni<List<InvitationToken>> list() {
    return InvitationToken.listAll();
  }

  @WithTransaction
  public Uni<InvitationToken> create(InvitationToken token) {
    token.token = UUID.randomUUID();
    return findUserByName(jwt.getName())
      .chain(createdBy -> {
        token.createdBy = createdBy;
        return token.persistAndFlush();
      });
  }

  @WithTransaction
  public Uni<InvitationToken> deactivate(long id) {
    return InvitationToken.<InvitationToken>findById(id)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(t -> {
        t.active = false;
        return t.persistAndFlush();
      });
  }

  @WithTransaction
  public Uni<Void> delete(long id) {
    return InvitationToken.deleteById(id).replaceWithVoid();
  }

  protected Uni<InvitationToken> findByToken(UUID tokenValue) {
    return InvitationToken.<InvitationToken>find("token", tokenValue).firstResult();
  }

  protected Uni<User> findUserByName(String name) {
    return User.<User>find("name", name).firstResult();
  }

  @WithSession
  public Uni<InvitationToken> validate(UUID tokenValue) {
    return findByToken(tokenValue)
      .map(t -> {
        if (t == null || !t.active || t.expiresAt.isBefore(ZonedDateTime.now())) {
          throw new ClientErrorException("Token ungültig oder abgelaufen", Response.Status.GONE);
        }
        return t;
      });
  }

  @WithTransaction
  public Uni<User> register(UUID tokenValue, String name, String email, String password) {
    return findByToken(tokenValue)
      .map(t -> {
        if (t == null || !t.active || t.expiresAt.isBefore(ZonedDateTime.now())) {
          throw new ClientErrorException("Token ungültig oder abgelaufen", Response.Status.GONE);
        }
        return t;
      })
      .chain(t -> {
        if (name == null || !name.matches("^[a-zA-Z0-9_-]{3,30}$")) {
          throw new ClientErrorException(
            "Benutzername muss 3–30 Zeichen lang sein und darf nur Buchstaben, Ziffern, - und _ enthalten",
            Response.Status.BAD_REQUEST);
        }
        User user = new User();
        user.name = name;
        user.email = email;
        user.setPassword(BcryptUtil.bcryptHash(password));
        user.roles = List.of(t.role);
        user.usedInvitation = t;
        user.emailVerificationToken = UUID.randomUUID();
        return user.<User>persistAndFlush()
          .chain(savedUser -> {
            t.lastUsedAt = ZonedDateTime.now();
            return t.persistAndFlush().replaceWith(savedUser);
          })
          .call(savedUser -> emailVerificationService.sendVerificationMail(savedUser));
      });
  }
}