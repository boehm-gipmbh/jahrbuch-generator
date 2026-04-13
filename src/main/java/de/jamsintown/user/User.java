package de.jamsintown.user;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {

  @Column(unique = true, nullable = false)
  public String name;

  @Column(unique = true, nullable = false)
  public String email;

  @Column(nullable = false)
  String password;

  @CreationTimestamp
  @Column(updatable = false, nullable = false)
  public ZonedDateTime created;

  @Version
  public int version;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "id"))
  @Column(name = "role")
  public List<String> roles;

  @Column(nullable = false)
  public boolean active = true;

  @Column(name = "email_verified", nullable = false)
  public boolean emailVerified = false;

  @Column(name = "email_verification_token")
  public UUID emailVerificationToken;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "used_invitation_id")
  public InvitationToken usedInvitation;

  @Transient
  public ZonedDateTime invitationExpiresAt;

  @Transient
  public Long usedInvitationId;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
    name = "user_groups",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "group_id")
  )
  public List<Gruppe> groups = new java.util.ArrayList<>();

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "active_group_id")
  public Gruppe activeGroup;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "managed_group_id")
  public Gruppe managedGroup;

  @JsonProperty("password")
  public void setPassword(String password) {
    this.password = password;
  }
}
