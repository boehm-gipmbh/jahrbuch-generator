package de.jamsintown.user;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class InvitationDeliveryPoller {

  private static final Logger LOG = Logger.getLogger(InvitationDeliveryPoller.class);
  private static final Set<String> FINAL_STATES = Set.of("delivered", "bounced", "complained");

  @Inject
  InvitationDeliveryPoller self;

  @Inject
  InvitationEmailService emailService;

  record PendingSend(long id, String resendMessageId) {}

  @Scheduled(every = "5m", delayed = "2m")
  public Uni<Void> poll() {
    return self.loadPending()
        .chain(sends -> processAll(sends, 0))
        .invoke(count -> { if (count > 0) LOG.infof("Delivery-Status für %d Sends aktualisiert", count); })
        .replaceWithVoid()
        .onFailure().recoverWithItem(err -> {
          LOG.errorf("Delivery-Status-Poll fehlgeschlagen: %s", err.getMessage());
          return null;
        });
  }

  @WithSession
  public Uni<List<PendingSend>> loadPending() {
    ZonedDateTime cutoff = ZonedDateTime.now().minusHours(48);
    return InvitationSend.<InvitationSend>find(
        "resendMessageId IS NOT NULL AND sentAt > ?1 AND (deliveryStatus IS NULL OR deliveryStatus NOT IN ('delivered', 'bounced', 'complained'))",
        cutoff
    ).list()
    .map(sends -> sends.stream()
        .map(s -> new PendingSend(s.id, s.resendMessageId))
        .toList());
  }

  @WithTransaction
  public Uni<Void> saveDeliveryStatus(long sendId, String deliveryStatus) {
    return InvitationSend.<InvitationSend>findById(sendId)
        .chain(s -> {
          if (s == null) return Uni.createFrom().voidItem();
          s.deliveryStatus = deliveryStatus;
          return s.<InvitationSend>persistAndFlush().replaceWithVoid();
        });
  }

  private Uni<Integer> processAll(List<PendingSend> sends, int updated) {
    if (sends.isEmpty()) return Uni.createFrom().item(updated);
    PendingSend head = sends.get(0);
    List<PendingSend> tail = sends.subList(1, sends.size());
    return emailService.getDeliveryStatus(head.resendMessageId())
        .chain(status -> {
          if (status == null || status.equals("unknown")) {
            return processAll(tail, updated);
          }
          return self.saveDeliveryStatus(head.id(), status)
              .chain(() -> processAll(tail, updated + 1));
        });
  }
}
