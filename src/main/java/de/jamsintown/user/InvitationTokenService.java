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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class InvitationTokenService {

  private final JsonWebToken jwt;
  private final EmailVerificationService emailVerificationService;
  private final GruppeService gruppeService;

  @Inject
  public InvitationTokenService(JsonWebToken jwt, EmailVerificationService emailVerificationService,
      GruppeService gruppeService) {
    this.jwt = jwt;
    this.emailVerificationService = emailVerificationService;
    this.gruppeService = gruppeService;
  }

  @WithSession
  public Uni<List<InvitationToken>> list() {
    // Zwei separate Queries um MultipleBagFetchException zu vermeiden.
    // 1) Tokens mit registeredUsers laden
    return InvitationToken.<InvitationToken>find(
        "FROM InvitationToken t LEFT JOIN FETCH t.registeredUsers"
      ).list()
      .chain(tokens -> {
        List<Long> groupIds = tokens.stream()
            .filter(t -> t.group != null)
            .map(t -> t.group.id)
            .distinct()
            .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
          tokens.forEach(t -> t.members = t.registeredUsers != null ? t.registeredUsers : List.of());
          return Uni.createFrom().item(tokens);
        }
        // 2) Gruppen-Mitglieder separat laden und den Gruppen zuordnen
        return User.<User>find(
            "FROM User u JOIN FETCH u.groups g WHERE g.id IN ?1", groupIds
          ).list()
          .map(groupUsers -> {
            Map<Long, List<User>> membersByGroupId = new HashMap<>();
            groupUsers.forEach(u ->
                u.groups.stream()
                    .filter(g -> groupIds.contains(g.id))
                    .forEach(g -> membersByGroupId
                        .computeIfAbsent(g.id, k -> new ArrayList<>())
                        .add(u))
            );
            tokens.forEach(t -> t.members = t.group != null
                ? membersByGroupId.getOrDefault(t.group.id, List.of())
                : t.registeredUsers != null ? t.registeredUsers : List.of());
            return tokens;
          });
      });
  }

  @WithTransaction
  public Uni<InvitationToken> create(InvitationToken token) {
    token.token = UUID.randomUUID();
    return findUserByName(jwt.getName())
      .chain(createdBy -> {
        token.createdBy = createdBy;
        if (token.label != null && !token.label.isBlank()) {
          return gruppeService.findOrCreate(token.label)
            .chain(gruppe -> {
              token.group = gruppe;
              return token.persistAndFlush();
            });
        }
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
  public Uni<InvitationToken> reactivate(long id) {
    return InvitationToken.<InvitationToken>findById(id)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(t -> {
        t.active = true;
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
    return User.<User>find("name", name.toLowerCase()).firstResult();
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
        if (password == null || password.length() < 8
            || !password.matches(".*[A-Z].*")
            || !password.matches(".*[a-z].*")
            || !password.matches(".*[0-9].*")
            || !password.matches(".*[^a-zA-Z0-9].*")) {
          throw new ClientErrorException(
            "Passwort muss mindestens 8 Zeichen, einen Großbuchstaben, einen Kleinbuchstaben, eine Zahl und ein Sonderzeichen enthalten",
            Response.Status.BAD_REQUEST);
        }
        User user = new User();
        user.name = name.toLowerCase();
        user.email = email.toLowerCase();
        user.setPassword(BcryptUtil.bcryptHash(password));
        user.roles = List.of(t.role);
        user.usedInvitation = t;
        user.emailVerificationToken = UUID.randomUUID();
        return user.<User>persistAndFlush()
          .chain(savedUser -> {
            t.lastUsedAt = ZonedDateTime.now();
            return t.persistAndFlush().replaceWith(savedUser);
          })
          .chain(savedUser -> {
            if (t.group != null) {
              return gruppeService.addToGroup(savedUser, t.group);
            }
            return Uni.createFrom().item(savedUser);
          })
          .call(savedUser -> emailVerificationService.sendVerificationMail(savedUser));
      });
  }
}