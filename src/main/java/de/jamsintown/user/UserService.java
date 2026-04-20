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

  private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(UserService.class);

  @Inject
  UserService self;

  private final JsonWebToken jwt;
  private final ReminderEmailService reminderEmailService;

  @Inject
  public UserService(JsonWebToken jwt, ReminderEmailService reminderEmailService) {
    this.jwt = jwt;
    this.reminderEmailService = reminderEmailService;
  }

  @WithTransaction
  public Uni<Void> persistReminderResendId(long sendId, String resendId) {
    if (resendId == null) return Uni.createFrom().voidItem();
    return ReminderSend.<ReminderSend>findById(sendId)
        .chain(s -> {
          if (s == null) return Uni.createFrom().voidItem();
          s.resendMessageId = resendId;
          return s.<ReminderSend>persistAndFlush().replaceWithVoid();
        });
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
                ReminderSend send = new ReminderSend();
                send.user = target;
                return send.<ReminderSend>persistAndFlush()
                    .invoke(savedSend -> {
                      reminderEmailService.sendReminderMail(target, groupName)
                          .chain(resendId -> self.persistReminderResendId(savedSend.id, resendId))
                          .subscribe().with(
                              v -> LOG.infof("Reminder an %s gesendet", target.email),
                              err -> LOG.errorf("Reminder an %s fehlgeschlagen: %s", target.email, err.getMessage()));
                    })
                    .replaceWithVoid();
              });
            }));
  }

  @WithSession
  public Uni<java.util.List<java.util.Map<String, String>>> getReminderSends(long userId) {
    return ReminderSend.<ReminderSend>find("user.id = ?1 ORDER BY sentAt DESC", userId)
        .list()
        .map(sends -> sends.stream().map(s -> {
          java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
          m.put("id", String.valueOf(s.id));
          m.put("sentAt", s.sentAt.toString());
          m.put("resendMessageId", s.resendMessageId != null ? s.resendMessageId : "");
          m.put("deliveryStatus", s.deliveryStatus != null ? s.deliveryStatus : "");
          return m;
        }).collect(java.util.stream.Collectors.toList()));
  }

  @WithSession
  public Uni<String[]> loadReminderSendFields(long sendId) {
    return ReminderSend.<ReminderSend>findById(sendId)
        .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
        .map(s -> new String[]{String.valueOf(s.id), s.sentAt.toString(), s.resendMessageId});
  }

  public Uni<java.util.Map<String, String>> getReminderSendStatus(long sendId) {
    return self.loadReminderSendFields(sendId)
        .chain(fields -> reminderEmailService.getDeliveryStatus(fields[2])
            .map(status -> {
              java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
              m.put("id", fields[0]);
              m.put("sentAt", fields[1]);
              m.put("resendMessageId", fields[2] != null ? fields[2] : "");
              m.put("status", status);
              return m;
            }));
  }

  @WithSession
  public Uni<String[]> loadLatestReminderSend(long userId) {
    return ReminderSend.<ReminderSend>find("user.id = ?1 ORDER BY sentAt DESC", userId)
        .firstResult()
        .map(s -> s == null ? null : new String[]{s.sentAt.toString(), s.resendMessageId});
  }

  public Uni<java.util.Map<String, String>> getReminderStatus(long userId) {
    return self.loadLatestReminderSend(userId)
        .chain(fields -> {
          if (fields == null) return Uni.createFrom().item(java.util.Map.of("status", "never_sent"));
          return reminderEmailService.getDeliveryStatus(fields[1])
              .map(status -> java.util.Map.of(
                  "sentAt", fields[0],
                  "resendMessageId", fields[1] != null ? fields[1] : "",
                  "status", status
              ));
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
