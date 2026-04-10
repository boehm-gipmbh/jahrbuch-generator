package de.jamsintown.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invitation_tokens")
public class InvitationToken extends PanacheEntity {

  @Column(unique = true, nullable = false)
  public UUID token;

  @Column
  public String label;

  @Column(nullable = false)
  public String role;

  @Column(nullable = false)
  public boolean active = true;

  @Column(name = "expires_at", nullable = false)
  public ZonedDateTime expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false, nullable = false)
  public ZonedDateTime createdAt;

  @Column(name = "last_used_at")
  public ZonedDateTime lastUsedAt;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "created_by", nullable = false)
  public User createdBy;

  @OneToMany(mappedBy = "usedInvitation", fetch = FetchType.EAGER)
  public List<User> registeredUsers;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "group_id")
  public Gruppe group;

  /** Effektive Mitglieder — wird in InvitationTokenService.list() befüllt.
   *  Bei Gruppen-Einladungen: alle Gruppen-Mitglieder.
   *  Bei einfachen Einladungen: registeredUsers. */
  @Transient
  @JsonIgnoreProperties({"groups", "activeGroup", "usedInvitation", "password"})
  public List<User> members;
}