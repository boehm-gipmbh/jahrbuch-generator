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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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
          if (user == null || user.managedGroup == null) {
            return Uni.createFrom().item(Collections.<InvitationToken>emptyList());
          }
          return InvitationToken.<InvitationToken>find(
              "FROM InvitationToken t LEFT JOIN FETCH t.registeredUsers LEFT JOIN FETCH t.group LEFT JOIN FETCH t.createdBy WHERE t.group.id = ?1",
              user.managedGroup.id
          ).list()
          .chain(tokens -> resolveMembers(tokens))
          .chain(tokens -> resolveSends(tokens));
        });
  }

  private Uni<List<InvitationToken>> listForAdmin() {
    return InvitationToken.<InvitationToken>find(
        "FROM InvitationToken t LEFT JOIN FETCH t.registeredUsers LEFT JOIN FETCH t.group LEFT JOIN FETCH t.createdBy"
      ).list()
      .chain(tokens -> resolveMembers(tokens))
      .chain(tokens -> resolveSends(tokens));
  }

  private Uni<List<InvitationToken>> resolveSends(List<InvitationToken> tokens) {
    List<Long> tokenIds = tokens.stream().map(t -> t.id).collect(Collectors.toList());
    if (tokenIds.isEmpty()) {
      tokens.forEach(t -> t.sends = List.of());
      return Uni.createFrom().item(tokens);
    }
    return InvitationSend.<InvitationSend>find(
        "FROM InvitationSend s JOIN FETCH s.token WHERE s.token.id IN ?1 ORDER BY s.sentAt DESC",
        tokenIds
    ).list().chain(allSends -> {
      List<String> sentToEmails = allSends.stream()
          .map(s -> s.sentTo).distinct().collect(Collectors.toList());
      if (sentToEmails.isEmpty()) {
        tokens.forEach(t -> t.sends = List.of());
        return Uni.createFrom().item(tokens);
      }
      return User.<User>find("email IN ?1", sentToEmails).list().map(registeredUsers -> {
        Map<String, String> emailToName = registeredUsers.stream()
            .collect(Collectors.toMap(u -> u.email, u -> u.name, (a, b) -> a));
        allSends.forEach(s -> s.registeredUserName = emailToName.get(s.sentTo));
        Map<Long, List<InvitationSend>> byToken = allSends.stream()
            .collect(Collectors.groupingBy(s -> s.token.id));
        tokens.forEach(t -> t.sends = byToken.getOrDefault(t.id, List.of()));
        return tokens;
      });
    });
  }

  /**
   * Für Tokens mit Gruppe: alle Mitglieder der Gruppe (aus user_groups) anzeigen.
   * Für Tokens ohne Gruppe: nur direkt registrierte User.
   */
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

    return User.<User>find(
        "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.usedInvitation LEFT JOIN FETCH u.groups g WHERE g.id IN ?1",
        groupIds
      ).list()
      .map(groupUsers -> {
        groupUsers.forEach(u -> {
          if (u.usedInvitation != null) {
            u.invitationExpiresAt = u.usedInvitation.expiresAt;
            u.usedInvitationId = u.usedInvitation.id;
          }
        });
        tokens.forEach(t -> {
          if (t.group != null) {
            t.members = groupUsers.stream()
                .filter(u -> u.groups != null && u.groups.stream().anyMatch(g -> g.id.equals(t.group.id)))
                .collect(Collectors.toList());
          } else {
            t.members = t.registeredUsers != null ? t.registeredUsers : List.of();
          }
        });
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
          // Gruppen-Admin darf user- und group-admin-Tokens für die eigene Gruppe erstellen
          if (createdBy.managedGroup == null) {
            throw new ClientErrorException(
                "Gruppen-Admin hat keine verwaltete Gruppe zugeordnet", Response.Status.FORBIDDEN);
          }
          if (token.role == null || token.role.isBlank()) {
            token.role = "user"; // Default-Rolle wenn nicht angegeben
          }
          if (!"user".equals(token.role) && !"group-admin".equals(token.role)) {
            throw new ClientErrorException(
                "Gruppen-Admin darf nur Rollen 'user' oder 'group-admin' vergeben", Response.Status.FORBIDDEN);
          }
          token.group = createdBy.managedGroup;
          token.label = token.group.name;
          return token.<InvitationToken>persistAndFlush()
              .call(() -> sendInvitationMailIfSet(token));
        }

        // Admin-Flow: optional E-Mail senden nach Persist
        if (token.label != null && !token.label.isBlank()) {
          return gruppeService.findOrCreate(token.label)
            .chain(gruppe -> {
              token.group = gruppe;
              return token.<InvitationToken>persistAndFlush()
                  .call(() -> sendInvitationMailIfSet(token));
            });
        }
        return token.<InvitationToken>persistAndFlush()
            .call(() -> sendInvitationMailIfSet(token));
      });
  }

  private Uni<Void> sendInvitationMailIfSet(InvitationToken token) {
    if (token.recipientEmail != null && !token.recipientEmail.isBlank()) {
      token.sentAt = ZonedDateTime.now();
      String resendId = invitationEmailService.sendInvitationMail(token);
      InvitationSend send = new InvitationSend();
      send.token = token;
      send.sentTo = token.recipientEmail;
      send.resendMessageId = resendId;
      return send.<InvitationSend>persistAndFlush().replaceWithVoid();
    }
    return Uni.createFrom().voidItem();
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
  public Uni<InvitationToken> extend(long id, ZonedDateTime newExpiresAt) {
    return InvitationToken.<InvitationToken>findById(id)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(t -> {
        t.expiresAt = newExpiresAt;
        return t.<InvitationToken>persistAndFlush();
      });
  }

  @WithTransaction
  public Uni<Void> resend(long id, String recipientEmail) {
    return InvitationToken.<InvitationToken>findById(id)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(t -> {
        String email = (recipientEmail != null && !recipientEmail.isBlank())
            ? recipientEmail : t.recipientEmail;
        if (email == null || email.isBlank()) {
          throw new ClientErrorException("Keine E-Mail-Adresse angegeben", Response.Status.BAD_REQUEST);
        }
        t.recipientEmail = email;
        t.sentAt = ZonedDateTime.now();
        return t.<InvitationToken>persistAndFlush()
            .call(() -> {
              String resendId = invitationEmailService.sendInvitationMail(t);
              InvitationSend send = new InvitationSend();
              send.token = t;
              send.sentTo = email;
              send.resendMessageId = resendId;
              return send.<InvitationSend>persistAndFlush().replaceWithVoid();
            });
      })
      .replaceWithVoid();
  }

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  @WithTransaction
  public Uni<List<BatchInvitationResult>> sendBatch(BatchInvitationRequest req) {



    if (req.entries() == null || req.entries().isEmpty()) {
      return Uni.createFrom().item(List.of());
    }

    Uni<InvitationToken> tokenUni;
    if (req.existingTokenId() != null) {
      tokenUni = InvitationToken.<InvitationToken>findById(req.existingTokenId())
          .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND));
    } else {
      tokenUni = findUserByNameWithGroups(jwt.getName()).chain(createdBy -> {
        InvitationToken t = new InvitationToken();
        t.token = UUID.randomUUID();
        t.role = req.defaultRole() != null ? req.defaultRole() : "user";
        t.expiresAt = req.expiresAt();
        t.label = req.label();
        t.createdBy = createdBy;
        boolean isGroupAdmin = jwt.getGroups().contains("group-admin") && !jwt.getGroups().contains("admin");
        if (isGroupAdmin && createdBy.managedGroup != null) {
          t.group = createdBy.managedGroup;
          t.label = t.group.name;
        } else if (t.label != null && !t.label.isBlank()) {
          return gruppeService.findOrCreate(t.label).chain(gruppe -> {
            t.group = gruppe;
            return t.<InvitationToken>persistAndFlush();
          });
        }
        return t.<InvitationToken>persistAndFlush();
      });
    }

    return tokenUni.chain(token -> {
      List<BatchInvitationResult> results = new ArrayList<>();
      List<BatchEntry> valid = new ArrayList<>();
      List<BatchEntry> invalid = new ArrayList<>();

      for (BatchEntry entry : req.entries()) {
        String email = entry.email() != null ? entry.email().trim().toLowerCase() : "";
        if (!EMAIL_PATTERN.matcher(email).matches()) {
          results.add(new BatchInvitationResult(entry.email(), "invalid", "Ungültige E-Mail-Adresse"));
          invalid.add(new BatchEntry(email.isBlank() ? entry.email() : email, entry.role()));
          continue;
        }
        valid.add(new BatchEntry(email, entry.role()));
      }

      List<String> emails = valid.stream().map(BatchEntry::email).collect(Collectors.toList());
      // Ungültige E-Mails ebenfalls für den alreadySent-Check einbeziehen (Dedup)
      List<String> allEmails = new ArrayList<>(emails);
      invalid.stream().map(BatchEntry::email)
          .filter(e -> e != null && !e.isBlank()).forEach(allEmails::add);

      if (allEmails.isEmpty()) {
        return Uni.createFrom().item(results);
      }

      return User.<User>find("SELECT u FROM User u LEFT JOIN FETCH u.groups WHERE u.email IN ?1", emails.isEmpty() ? List.of("__none__") : emails).list()
          .chain(existingUsers -> {
            // Gruppenweiter alreadySent-Check: Email gilt als versendet wenn sie in IRGENDEINEM Token der Gruppe vorkommt
            String sendQuery = token.group != null
                ? "sentTo IN ?1 AND token.id IN (SELECT t.id FROM InvitationToken t WHERE t.group.id = ?2)"
                : "sentTo IN ?1 AND token.id = ?2";
            Object queryParam = token.group != null ? token.group.id : token.id;
            return InvitationSend.<InvitationSend>find(sendQuery, allEmails, queryParam).list()
                .map(existingSends -> {
                  java.util.Set<String> alreadySent = existingSends.stream()
                      .map(s -> s.sentTo).collect(java.util.stream.Collectors.toSet());
                  return new Object[]{existingUsers, alreadySent};
                });
          })
          .chain(pair -> {
            @SuppressWarnings("unchecked")
            java.util.List<User> existingUsers = (java.util.List<User>) pair[0];
            @SuppressWarnings("unchecked")
            java.util.Set<String> alreadySent = (java.util.Set<String>) pair[1];
            java.util.Set<String> registered = existingUsers.stream()
                .map(u -> u.email).collect(java.util.stream.Collectors.toSet());
            java.util.Set<String> inGroup = token.group == null ? registered : existingUsers.stream()
                .filter(u -> u.groups != null && u.groups.stream().anyMatch(g -> g.id.equals(token.group.id)))
                .map(u -> u.email).collect(java.util.stream.Collectors.toSet());

            // Ungültige Einträge persistieren — nur wenn noch kein Eintrag existiert (Dedup)
            Uni<Void> persistInvalid = Uni.createFrom().voidItem();
            for (BatchEntry entry : invalid) {
              if (!alreadySent.contains(entry.email())) {
                InvitationSend s = new InvitationSend();
                s.token = token;
                s.sentTo = entry.email();
                s.status = "invalid";
                persistInvalid = persistInvalid.chain(() -> s.<InvitationSend>persistAndFlush().replaceWithVoid());
              }
            }
            final Uni<Void> finalPersistInvalid = persistInvalid;

            // Sequentiell verarbeiten — parallele persistAndFlush() kollidieren auf der Sequence-ID
            Uni<List<BatchInvitationResult>> chain = Uni.createFrom().item(results);
            for (BatchEntry entry : valid) {
              if (registered.contains(entry.email())) {
                boolean isInGroup = inGroup.contains(entry.email());
                String status = isInGroup ? "already_registered" : "registered_not_in_group";
                String msg = isInGroup ? "Bereits in der Gruppe" : "Account vorhanden, noch nicht in der Gruppe";
                results.add(new BatchInvitationResult(entry.email(), status, msg));
                if (!alreadySent.contains(entry.email())) {
                  InvitationSend send = new InvitationSend();
                  send.token = token;
                  send.sentTo = entry.email();
                  send.status = status;
                  chain = chain.chain(r -> send.<InvitationSend>persistAndFlush().replaceWith(r));
                }
                continue;
              }
              if (alreadySent.contains(entry.email())) {
                results.add(new BatchInvitationResult(entry.email(), "already_sent", "Bereits versendet"));
                continue;
              }
              String role = entry.role() != null && !entry.role().isBlank() ? entry.role() : token.role;
              InvitationSend send = new InvitationSend();
              send.token = token;
              send.sentTo = entry.email();
              InvitationToken mailToken = new InvitationToken();
              mailToken.token = token.token;
              mailToken.recipientEmail = entry.email();
              mailToken.label = token.label;
              mailToken.role = role;
              mailToken.expiresAt = token.expiresAt;
              mailToken.createdBy = token.createdBy;
              send.resendMessageId = invitationEmailService.sendInvitationMail(mailToken);
              final String sentTo = entry.email();
              chain = chain.chain(r -> send.<InvitationSend>persistAndFlush()
                  .map(s -> { r.add(new BatchInvitationResult(sentTo, "sent", "Versendet")); return r; }));
            }
            return chain.chain(r -> finalPersistInvalid.replaceWith(r));
          });
    });
  }

  @WithSession
  public Uni<java.util.Map<String, String>> getSendStatus(long sendId) {
    return InvitationSend.<InvitationSend>findById(sendId)
        .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
        .map(send -> {
          String status = invitationEmailService.getDeliveryStatus(send.resendMessageId);
          return java.util.Map.of(
              "sendId", String.valueOf(send.id),
              "sentTo", send.sentTo,
              "resendMessageId", send.resendMessageId != null ? send.resendMessageId : "",
              "status", status
          );
        });
  }

  @WithTransaction
  public Uni<InvitationSend> updateSendEmail(long sendId, String email) {
    return InvitationSend.<InvitationSend>findById(sendId)
        .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
        .chain(s -> {
          s.sentTo = email.toLowerCase().trim();
          s.status = EMAIL_PATTERN.matcher(s.sentTo).matches() ? "sent" : "invalid";
          return s.<InvitationSend>persistAndFlush();
        });
  }

  @WithTransaction
  public Uni<Void> deleteSend(long sendId) {
    return InvitationSend.deleteById(sendId).replaceWithVoid();
  }

  @WithTransaction
  public Uni<Void> delete(long id) {
    return io.quarkus.hibernate.reactive.panache.Panache.getSession()
        .chain(session -> session.createNativeQuery(
                "UPDATE users SET invitation_expires_at = (SELECT expires_at FROM invitation_tokens WHERE id = :id), " +
                "used_invitation_id = null WHERE used_invitation_id = :id")
            .setParameter("id", id)
            .executeUpdate()
            .chain(ignored -> session.createNativeQuery(
                    "DELETE FROM invitation_tokens WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate())
            .replaceWithVoid());
  }

  protected Uni<InvitationToken> findByToken(UUID tokenValue) {
    return InvitationToken.<InvitationToken>find("token", tokenValue).firstResult();
  }

  protected Uni<User> findUserByName(String name) {
    return User.<User>find("name", name.toLowerCase()).firstResult();
  }

  private Uni<User> findUserByNameWithGroups(String name) {
    return User.<User>find(
        "FROM User u LEFT JOIN FETCH u.groups LEFT JOIN FETCH u.activeGroup LEFT JOIN FETCH u.managedGroup WHERE u.name = ?1", name.toLowerCase()
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
              return gruppeService.addToGroup(savedUser, t.group)
                  .chain(u -> {
                    if ("group-admin".equals(t.role)) {
                      u.managedGroup = t.group;
                      return u.<User>persistAndFlush();
                    }
                    return Uni.createFrom().item(u);
                  });
            }
            return Uni.createFrom().item(savedUser);
          })
          .call(savedUser -> emailVerificationService.sendVerificationMail(savedUser));
      });
  }
}