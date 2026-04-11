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
import java.util.Collections;
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
  private final InvitationEmailService invitationEmailService;

  @Inject
  public InvitationTokenService(JsonWebToken jwt, EmailVerificationService emailVerificationService,
      GruppeService gruppeService, InvitationEmailService invitationEmailService) {
    this.jwt = jwt;
    this.emailVerificationService = emailVerificationService;
    this.gruppeService = gruppeService;
    this.invitationEmailService = invitationEmailService;
  }

  @WithSession
  public Uni<List<InvitationToken>> list() {
    boolean isGroupAdmin = jwt.getGroups().contains("group-admin") && !jwt.getGroups().contains("admin");
    if (isGroupAdmin) {
      return listForGroupAdmin();
    }
    return listForAdmin();
  }

  private Uni<List<InvitationToken>> listForGroupAdmin() {
    return findUserByNameWithGroups(jwt.getName())
        .chain(user -> {
          if (user == null || user.groups.isEmpty()) {
            return Uni.createFrom().item(Collections.<InvitationToken>emptyList());
          }
          List<Long> userGroupIds = user.groups.stream().map(g -> g.id).collect(Collectors.toList());
          return InvitationToken.<InvitationToken>find(
              "FROM InvitationToken t LEFT JOIN FETCH t.registeredUsers WHERE t.group.id IN ?1",
              userGroupIds
          ).list()
          .chain(tokens -> resolveMembers(tokens));
        });
  }

  private Uni<List<InvitationToken>> listForAdmin() {
    // Zwei separate Queries um MultipleBagFetchException zu vermeiden.
    // 1) Tokens mit registeredUsers laden
    return InvitationToken.<InvitationToken>find(
        "FROM InvitationToken t LEFT JOIN FETCH t.registeredUsers"
      ).list()
      .chain(tokens -> resolveMembers(tokens));
  }

  private Uni<List<InvitationToken>> resolveMembers(List<InvitationToken> tokens) {
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
  }

  @WithTransaction
  public Uni<InvitationToken> create(InvitationToken token) {
    token.token = UUID.randomUUID();
    boolean isGroupAdmin = jwt.getGroups().contains("group-admin") && !jwt.getGroups().contains("admin");

    return findUserByNameWithGroups(jwt.getName())
      .chain(createdBy -> {
        token.createdBy = createdBy;

        if (isGroupAdmin) {
          // Gruppen-Admin darf nur user-Tokens für die eigene Gruppe erstellen
          if (createdBy.groups == null || createdBy.groups.isEmpty()) {
            throw new ClientErrorException(
                "Gruppen-Admin ist keiner Gruppe zugeordnet", Response.Status.FORBIDDEN);
          }
          token.role = "user";
          token.group = createdBy.groups.get(0);
          token.label = token.group.name;
          token.recipientEmail = null; // Gruppen-Admin teilt Link manuell
          return token.<InvitationToken>persistAndFlush();
        }

        // Admin-Flow: optional E-Mail senden nach Persist
        if (token.label != null && !token.label.isBlank()) {
          return gruppeService.findOrCreate(token.label)
            .chain(gruppe -> {
              token.group = gruppe;
              return token.<InvitationToken>persistAndFlush()
                  .invoke(() -> sendInvitationMailIfSet(token));
            });
        }
        return token.<InvitationToken>persistAndFlush()
            .invoke(() -> sendInvitationMailIfSet(token));
      });
  }

  private void sendInvitationMailIfSet(InvitationToken token) {
    if (token.recipientEmail != null && !token.recipientEmail.isBlank()) {
      invitationEmailService.sendInvitationMail(token);
    }
  }

  @WithTransaction
  public Uni<InvitationToken> deactivate(long id) {
    return InvitationToken.<InvitationToken>findById(id)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(t -> {
        t.active = false;
        return t.<InvitationToken>persistAndFlush();
      });
  }

  @WithTransaction
  public Uni<InvitationToken> reactivate(long id) {
    return InvitationToken.<InvitationToken>findById(id)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(t -> {
        t.active = true;
        return t.<InvitationToken>persistAndFlush();
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

  private Uni<User> findUserByNameWithGroups(String name) {
    return User.<User>find(
        "FROM User u LEFT JOIN FETCH u.groups WHERE u.name = ?1", name.toLowerCase()
    ).list().map(users -> users.isEmpty() ? null : users.get(0));
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
        // group-admin bekommt zusätzlich die user-Rolle, damit er die App nutzen kann
        user.roles = "group-admin".equals(t.role)
            ? List.of("group-admin", "user")
            : List.of(t.role);
        user.usedInvitation = t;
        user.emailVerificationToken = UUID.randomUUID();
        return user.<User>persistAndFlush()
          .chain(savedUser -> {
            t.lastUsedAt = ZonedDateTime.now();
            return t.<InvitationToken>persistAndFlush().replaceWith(savedUser);
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