package de.jamsintown.user;

import de.jamsintown.bild.Bild;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hibernate.ObjectNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserService {

  private final JsonWebToken jwt;
  private final ReminderEmailService reminderEmailService;

  @Inject
  public UserService(JsonWebToken jwt, ReminderEmailService reminderEmailService) {
    this.jwt = jwt;
    this.reminderEmailService = reminderEmailService;
  }

  public Uni<User> findById(long id) {
    return User.<User>findById(id)
      .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "User"));
  }

  public Uni<User> findByEmail(String email) {
    return User.<User>find("email", email.toLowerCase()).firstResult();
  }

  public Uni<User> findByName(String name) {
    // activeGroup explizit via LEFT JOIN FETCH laden — Hibernate Reactive
    // ignoriert FetchType.EAGER für @ManyToOne außerhalb einer offenen Session.
    return User.<User>find(
        "FROM User u LEFT JOIN FETCH u.groups LEFT JOIN FETCH u.activeGroup LEFT JOIN FETCH u.managedGroup LEFT JOIN FETCH u.usedInvitation WHERE u.name = ?1", name.toLowerCase())
        .list()
        .map(users -> {
          if (users.isEmpty()) return null;
          User u = users.get(0);
          if (u.usedInvitation != null) {
            u.invitationExpiresAt = u.usedInvitation.expiresAt;
            u.usedInvitationId = u.usedInvitation.id;
          }
          return u;
        });
  }

  public Uni<List<User>> list() {
    return User.listAll();
  }

  @WithTransaction
  public Uni<User> create(User user) {
    user.password = BcryptUtil.bcryptHash(user.password);
    return user.persistAndFlush();
  }

  @WithTransaction
  public Uni<User> update(User user) {
    return findById(user.id).chain(u -> {
        user.setPassword(u.password);
        return User.getSession();
      })
      .chain(s -> s.merge(user));
  }

  @WithTransaction
  public Uni<User> updateEmail(long id, String email) {
    return findById(id).chain(u -> {
      u.email = email.toLowerCase().trim();
      return u.<User>persistAndFlush();
    });
  }

  @WithTransaction
  public Uni<User> updateInvitationExpiry(long id, java.time.ZonedDateTime expiresAt) {
    return assertGroupAdminCanActOn(id)
        .chain(__ -> findById(id))
        .chain(u -> {
          u.invitationExpiresAt = expiresAt;
          return u.<User>persistAndFlush();
        });
  }

  public Uni<User> getCurrentUser() {
    return findByName(jwt.getName());
  }

  public static boolean matches(User user, String password) {
    return BcryptUtil.matches(password, user.password);
  }

  /**
   * Prüft, ob der eingeloggte group-admin auf den Ziel-User zugreifen darf
   * (Ziel-User muss Mitglied der aktiven Gruppe des group-admins sein).
   * Für echte Admins wird die Prüfung übersprungen.
   */
  Uni<Void> assertGroupAdminCanActOn(long targetId) {
    boolean isGroupAdmin = jwt.getGroups().contains("group-admin")
        && !jwt.getGroups().contains("admin");
    if (!isGroupAdmin) return Uni.createFrom().voidItem();
    return getCurrentUser()
        .chain(admin -> {
          if (admin == null || admin.managedGroup == null) {
            throw new ClientErrorException(Response.Status.FORBIDDEN);
          }
          return User.count(
              "SELECT COUNT(u) FROM User u JOIN u.groups g WHERE u.id = ?1 AND g.id = ?2",
              targetId, admin.managedGroup.id
          ).map(count -> {
            if (count == 0) throw new ClientErrorException(Response.Status.FORBIDDEN);
            return (Void) null;
          });
        });
  }

  @WithTransaction
  public Uni<Void> delete(long id) {
    return assertGroupAdminCanActOn(id)
        .chain(__ -> findById(id)
            .chain(u -> de.jamsintown.bild.Bild.count("user", u)
                .flatMap(bilder -> de.jamsintown.text.Text.count("user", u)
                    .flatMap(texte -> de.jamsintown.story.Story.count("user", u)
                        .chain(stories -> {
                            if (bilder > 0 || texte > 0 || stories > 0) {
                                throw new ClientErrorException(
                                    "User hat noch Inhalte (" + bilder + " Bilder, " + texte + " Texte, " + stories + " Stories). Bitte zuerst deaktivieren.",
                                    Response.Status.CONFLICT);
                            }
                            return u.delete();
                        })))));
  }

  @WithTransaction
  public Uni<User> deactivate(long id) {
    return assertGroupAdminCanActOn(id)
        .chain(__ -> findById(id).chain(u -> {
          u.active = false;
          return u.persistAndFlush();
        }));
  }

  @WithTransaction
  public Uni<User> reactivate(long id) {
    return assertGroupAdminCanActOn(id)
        .chain(__ -> findById(id).chain(u -> {
          u.active = true;
          return u.persistAndFlush();
        }));
  }

  @WithTransaction
  public Uni<User> promoteToGroupAdmin(long id, long groupId) {
    return assertGroupAdminCanActOn(id)
        .chain(__ -> Gruppe.<Gruppe>findById(groupId)
            .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
            .chain(gruppe -> findById(id).chain(u -> {
              if (u.roles == null || !u.roles.contains("group-admin")) {
                u.roles = new ArrayList<>(u.roles != null ? u.roles : List.of());
                u.roles.add("group-admin");
              }
              u.managedGroup = gruppe;
              return u.persistAndFlush();
            })));
  }

  @WithTransaction
  public Uni<User> demoteFromGroupAdmin(long id) {
    return assertGroupAdminCanActOn(id)
        .chain(__ -> findById(id).chain(u -> {
          u.roles = u.roles == null ? List.of() : u.roles.stream()
              .filter(r -> !"group-admin".equals(r))
              .collect(Collectors.toList());
          u.managedGroup = null;
          return u.persistAndFlush();
        }));
  }

  @WithTransaction
  public Uni<Void> sendReminder(long id) {
    return assertGroupAdminCanActOn(id)
        .chain(__ -> getCurrentUser()
            .chain(admin -> {
              String groupName = admin.managedGroup != null ? admin.managedGroup.name : null;
              return findById(id).chain(target -> {
                if (target.email == null || target.email.isBlank()) {
                  throw new ClientErrorException("User hat keine E-Mail-Adresse", Response.Status.BAD_REQUEST);
                }
                String resendId = reminderEmailService.sendReminderMail(target, groupName);
                ReminderSend send = new ReminderSend();
                send.user = target;
                send.resendMessageId = resendId;
                return send.<ReminderSend>persistAndFlush().replaceWithVoid();
              });
            }));
  }

  @WithSession
  public Uni<java.util.Map<String, String>> getReminderStatus(long userId) {
    return ReminderSend.<ReminderSend>find("user.id = ?1 ORDER BY sentAt DESC", userId)
        .firstResult()
        .map(send -> {
          if (send == null) return java.util.Map.of("status", "never_sent");
          String status = send.resendMessageId != null
              ? reminderEmailService.getDeliveryStatus(send.resendMessageId) : "unknown";
          return java.util.Map.of(
              "sentAt", send.sentAt.toString(),
              "resendMessageId", send.resendMessageId != null ? send.resendMessageId : "",
              "status", status
          );
        });
  }

  @WithTransaction
  public Uni<User> changePassword(String currentPassword, String newPassword) {
    return getCurrentUser()
      .chain(u -> {
        if (!matches(u, currentPassword)) {
          throw new ClientErrorException("Current password does not match", Response.Status.CONFLICT);
        }
        u.setPassword(BcryptUtil.bcryptHash(newPassword));
        return u.persistAndFlush();
      });
  }

}
