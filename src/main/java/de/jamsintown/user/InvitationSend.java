package de.jamsintown.user;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "invitation_sends")
public class InvitationSend extends PanacheEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "token_id", nullable = false)
  public InvitationToken token;

  @Column(name = "sent_to", nullable = false)
  public String sentTo;

  @CreationTimestamp
  @Column(name = "sent_at", nullable = false, updatable = false)
  public ZonedDateTime sentAt;
}
