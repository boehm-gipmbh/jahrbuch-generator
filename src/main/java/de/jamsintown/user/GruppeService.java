package de.jamsintown.user;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GruppeService {

  /** Findet eine Gruppe anhand des Namens oder legt sie neu an. */
  @WithTransaction
  public Uni<Gruppe> findOrCreate(String name) {
    return Gruppe.<Gruppe>find("name", name).firstResult()
      .chain(existing -> {
        if (existing != null) return Uni.createFrom().item(existing);
        Gruppe g = new Gruppe();
        g.name = name;
        return g.persistAndFlush();
      });
  }

  /** Fügt einen User einer Gruppe hinzu und setzt sie als aktive Gruppe. */
  @WithTransaction
  public Uni<User> addToGroup(User user, Gruppe gruppe) {
    if (!user.groups.contains(gruppe)) {
      user.groups.add(gruppe);
    }
    user.activeGroup = gruppe;
    return user.persistAndFlush();
  }

  /** Wechselt die aktive Gruppe — User muss Mitglied sein. */
  @WithTransaction
  public Uni<User> setActiveGroup(User user, long groupId) {
    return Gruppe.<Gruppe>findById(groupId)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(gruppe -> {
        if (!user.groups.contains(gruppe)) {
          throw new ClientErrorException("User ist kein Mitglied dieser Gruppe", Response.Status.FORBIDDEN);
        }
        user.activeGroup = gruppe;
        return user.persistAndFlush();
      });
  }

  /** Verlässt die aktive Gruppe (setzt activeGroup auf null). */
  @WithTransaction
  public Uni<User> clearActiveGroup(User user) {
    user.activeGroup = null;
    return user.persistAndFlush();
  }
}
