package de.jamsintown.config;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "app_config")
public class AppConfig extends PanacheEntityBase {

  @Id
  @Column(name = "key", nullable = false, length = 255)
  public String key;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String value;

  @CreationTimestamp
  @Column(name = "updated_at", updatable = false)
  public ZonedDateTime updatedAt;

  public AppConfig() {
  }

  public AppConfig(String key, String value) {
    this.key = key;
    this.value = value;
  }
}