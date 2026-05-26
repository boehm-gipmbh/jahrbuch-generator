package de.jamsintown.notification;

import de.jamsintown.announcement.AnnouncementMailService;
import de.jamsintown.announcement.Recipient;
import de.jamsintown.reaction.Reaction;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);

    private final AnnouncementMailService mailService;

    @Inject
    public NotificationService(AnnouncementMailService mailService) {
        this.mailService = mailService;
    }

    @WithTransaction
    public Uni<Void> createReportNotifications(User reporter, Reaction.TargetType targetType, Long targetId, String message) {
        return findGroupAdmins(reporter).chain(admins -> {
            if (admins.isEmpty()) {
                LOG.infof("Keine Group-Admins für Reporter %s gefunden", reporter.name);
                return Uni.createFrom().voidItem();
            }

            List<Notification> notifications = admins.stream().map(admin -> {
                Notification n = new Notification();
                n.recipient = admin;
                n.targetType = targetType;
                n.targetId = targetId;
                n.type = Notification.NotificationType.REPORT;
                n.reporterName = reporter.name;
                n.message = message;
                return n;
            }).toList();

            Uni<Void> persist = Notification.persist(notifications).replaceWithVoid();

            List<Recipient> recipients = admins.stream()
                .map(a -> new Recipient(a.id, a.name, a.email))
                .toList();

            String subject = "Inhalt gemeldet von " + reporter.name;
            String messageHtml = (message != null && !message.isBlank())
                ? "<p><strong>Begründung:</strong> %s</p>".formatted(message)
                : "";
            String body = "<p>Der Nutzer <strong>%s</strong> hat einen Inhalt vom Typ <strong>%s</strong> (ID: %d) gemeldet.</p>"
                .formatted(reporter.name, targetType.name(), targetId)
                + messageHtml
                + "<p>Bitte prüfe den Inhalt und entscheide über eine Löschung.</p>";

            return persist.chain(() ->
                mailService.sendToAll(subject, body, recipients, null, null)
                    .invoke(result -> LOG.infof("Report-Mails: %d gesendet, %d fehlgeschlagen", result.sent, result.failed))
                    .replaceWithVoid()
            );
        });
    }

    public Uni<List<NotificationDTO>> getUnread(Long userId) {
        return Notification.<Notification>list(
            "recipient.id = ?1 AND readAt IS NULL ORDER BY createdAt DESC", userId
        ).map(list -> list.stream().map(NotificationDTO::from).toList());
    }

    public Uni<Long> countUnread(Long userId) {
        return Notification.count("recipient.id = ?1 AND readAt IS NULL", userId);
    }

    @WithTransaction
    public Uni<Void> markRead(Long notificationId, Long userId) {
        return Notification.<Notification>findById(notificationId).chain(n -> {
            if (n == null || !n.recipient.id.equals(userId)) throw new NotFoundException();
            n.readAt = ZonedDateTime.now();
            return n.<Notification>persistAndFlush().replaceWithVoid();
        });
    }

    @WithTransaction
    public Uni<Void> markAllRead(Long userId) {
        return Notification.<Notification>list("recipient.id = ?1 AND readAt IS NULL", userId)
            .chain(list -> {
                ZonedDateTime now = ZonedDateTime.now();
                list.forEach(n -> n.readAt = now);
                return Notification.persist(list).replaceWithVoid();
            });
    }

    private Uni<List<User>> findGroupAdmins(User reporter) {
        if (reporter.activeGroup == null) {
            return User.<User>list(
                "SELECT DISTINCT u FROM User u JOIN u.managedGroups g WHERE SIZE(u.managedGroups) > 0"
            );
        }
        return User.<User>list(
            "SELECT DISTINCT u FROM User u JOIN u.managedGroups g WHERE g.id = ?1",
            reporter.activeGroup.id
        );
    }
}
