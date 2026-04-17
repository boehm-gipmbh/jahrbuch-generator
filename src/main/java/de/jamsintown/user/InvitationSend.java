package de.jamsintown.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "invitation_sends")
public class InvitationSend extends PanacheEntity {

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "token_id", nullable = false)
  public InvitationToken token;

  @Column(name = "sent_to", nullable = false)
  public String sentTo;

  @CreationTimestamp
  @Column(name = "sent_at", nullable = false, updatable = false)
  public ZonedDateTime sentAt;

  @Column(name = "resend_message_id")
  public String resendMessageId;

  @Column(nullable = false)
  public String status = "sent";

  /** Vom Backend befüllt: Name des registrierten Users, falls sentTo-Email bereits ein Konto hat. */
  @Transient
  @JsonProperty
  public String registeredUserName;
}
