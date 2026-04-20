package de.jamsintown.user;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class InvitationDeliveryPoller {

  private static final Logger LOG = Logger.getLogger(InvitationDeliveryPoller.class);

  @Inject
  InvitationDeliveryPoller self;

  @Inject
  InvitationEmailService emailService;

  @Inject
  ReminderEmailService reminderEmailService;

  record PendingSend(long id, String resendMessageId) {}

  @PostConstruct
  void init() {
    LOG.info("InvitationDeliveryPoller initialisiert");
  }

  private static final String PENDING_CONDITION =
      "resendMessageId IS NOT NULL AND sentAt > ?1 AND (deliveryStatus IS NULL OR deliveryStatus NOT IN ('delivered', 'bounced', 'complained', 'suppressed'))";

  @Scheduled(every = "5m", delayed = "2m")
  public Uni<Void> poll() {
    LOG.info("Delivery-Status-Poll gestartet");
    ZonedDateTime cutoff = ZonedDateTime.now().minusHours(48);
    return self.loadPendingInvitations(cutoff)
        .chain(sends -> processAll(sends, 0, false))
        .chain(invCount -> self.loadPendingReminders(cutoff)
            .chain(sends -> processAll(sends, 0, true))
            .map(remCount -> invCount + remCount))
        .invoke(count -> { if (count > 0) LOG.infof("Delivery-Status für %d Sends aktualisiert", count); })
        .replaceWithVoid()
        .onFailure().recoverWithItem(err -> {
          LOG.errorf("Delivery-Status-Poll fehlgeschlagen: %s", err.getMessage());
          return null;
        });
  }

  @WithSession
  public Uni<List<PendingSend>> loadPendingInvitations(ZonedDateTime cutoff) {
    return InvitationSend.<InvitationSend>find(PENDING_CONDITION, cutoff).list()
        .map(sends -> sends.stream().map(s -> new PendingSend(s.id, s.resendMessageId)).toList());
  }

  @WithSession
  public Uni<List<PendingSend>> loadPendingReminders(ZonedDateTime cutoff) {
    return ReminderSend.<ReminderSend>find(PENDING_CONDITION, cutoff).list()
        .map(sends -> sends.stream().map(s -> new PendingSend(s.id, s.resendMessageId)).toList());
  }

  @WithTransaction
  public Uni<Void> saveInvitationDeliveryStatus(long sendId, String deliveryStatus) {
    return InvitationSend.<InvitationSend>findById(sendId)
        .chain(s -> {
          if (s == null) return Uni.createFrom().voidItem();
          s.deliveryStatus = deliveryStatus;
          return s.<InvitationSend>persistAndFlush().replaceWithVoid();
        });
  }

  @WithTransaction
  public Uni<Void> saveReminderDeliveryStatus(long sendId, String deliveryStatus) {
    return ReminderSend.<ReminderSend>findById(sendId)
        .chain(s -> {
          if (s == null) return Uni.createFrom().voidItem();
          s.deliveryStatus = deliveryStatus;
          return s.<ReminderSend>persistAndFlush().replaceWithVoid();
        });
  }

  private Uni<Integer> processAll(List<PendingSend> sends, int updated, boolean isReminder) {
    if (sends.isEmpty()) return Uni.createFrom().item(updated);
    PendingSend head = sends.get(0);
    List<PendingSend> tail = sends.subList(1, sends.size());
    Uni<String> statusUni = isReminder
        ? reminderEmailService.getDeliveryStatus(head.resendMessageId())
        : emailService.getDeliveryStatus(head.resendMessageId());
    return statusUni.chain(status -> {
      if (status == null || status.equals("unknown")) {
        return processAll(tail, updated, isReminder);
      }
      Uni<Void> save = isReminder
          ? self.saveReminderDeliveryStatus(head.id(), status)
          : self.saveInvitationDeliveryStatus(head.id(), status);
      return save.chain(() -> processAll(tail, updated + 1, isReminder));
    });
  }
}
