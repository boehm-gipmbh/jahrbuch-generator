package de.jamsintown.user;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "reminder_sends")
public class ReminderSend extends PanacheEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  public User user;

  @Column(name = "resend_message_id")
  public String resendMessageId;

  @CreationTimestamp
  @Column(name = "sent_at", updatable = false, nullable = false)
  public ZonedDateTime sentAt;
}
