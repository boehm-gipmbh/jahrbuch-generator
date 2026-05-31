package de.jamsintown.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "gruppen")
public class Gruppe extends PanacheEntity {

  @Column(nullable = false, unique = true)
  public String name;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false, nullable = false)
  public ZonedDateTime createdAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "pdf_settings", columnDefinition = "jsonb")
  public String pdfSettings;

  @JsonIgnore
  @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
  public List<User> members;
}
