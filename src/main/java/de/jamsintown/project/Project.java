package de.jamsintown.project;

import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.Set;

@Entity
@Table(
  name = "projects",
  uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name"})
  }
)
public class Project extends PanacheEntity {

  @Column(nullable = false)
  public String name;

  @ManyToMany
  @JoinTable(
          name = "projects_users",
          joinColumns = @JoinColumn(name = "project_id"),
          inverseJoinColumns = @JoinColumn(name = "users_id")
  )
  public Set<User> users;


  @CreationTimestamp
  @Column(updatable = false, nullable = false)
  public ZonedDateTime created;

  @Version
  public int version;
}
