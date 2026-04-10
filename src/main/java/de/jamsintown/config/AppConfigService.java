package de.jamsintown.config;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet globale Anwendungskonfiguration aus der DB.
 * Caching mit 30-Sekunden-TTL für Performance.
 */
@ApplicationScoped
public class AppConfigService {

  private static class CacheEntry {
    String value;
    long timestamp;

    CacheEntry(String value) {
      this.value = value;
      this.timestamp = System.currentTimeMillis();
    }

    boolean isExpired() {
      return System.currentTimeMillis() - timestamp > 30000; // 30 Sekunden TTL
    }
  }

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  /** Gibt einen Config-Wert zurück, falls vorhanden, sonst null. */
  @WithSession
  public Uni<String> getValue(String key) {
    // Cache prüfen
    CacheEntry cached = cache.get(key);
    if (cached != null && !cached.isExpired()) {
      return Uni.createFrom().item(cached.value);
    }

    // Aus DB laden
    return AppConfig.<AppConfig>find("key", key).firstResult()
        .map(config -> {
          String value = config != null ? config.value : null;
          if (value != null) {
            cache.put(key, new CacheEntry(value));
          } else {
            cache.remove(key);
          }
          return value;
        });
  }

  /** Setzt einen Config-Wert und invalidiert Cache. */
  @WithTransaction
  public Uni<AppConfig> setValue(String key, String value) {
    cache.remove(key); // Cache invalidieren
    return AppConfig.<AppConfig>find("key", key).firstResult()
        .chain(existing -> {
          if (existing != null) {
            existing.value = value;
            return existing.persistAndFlush();
          } else {
            AppConfig config = new AppConfig(key, value);
            return config.persistAndFlush();
          }
        });
  }

  /** Laden aller Config-Werte (mit Cache-Invalidierung). */
  @WithSession
  public Uni<List<AppConfig>> listAll() {
    cache.clear(); // Cache invalidieren beim Abruf aller Werte
    return AppConfig.<AppConfig>findAll().list();
  }
}