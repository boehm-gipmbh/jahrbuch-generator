package de.jamsintown.auth;

import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken extends PanacheEntityBase {

  @Id
  @Column(name = "token", nullable = false)
  public UUID token;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  public User user;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false, nullable = false)
  public ZonedDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  public ZonedDateTime expiresAt;

  @Column(nullable = false)
  public boolean used = false;

  public PasswordResetToken() {
  }

  public PasswordResetToken(User user) {
    this.token = UUID.randomUUID();
    this.user = user;
    this.expiresAt = ZonedDateTime.now().plusHours(24);
  }

  public boolean isExpired() {
    return ZonedDateTime.now().isAfter(expiresAt);
  }

  public boolean isValid() {
    return !used && !isExpired();
  }
}