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

  /** Fügt einen User einer Gruppe hinzu und setzt sie als aktive Gruppe.
   *  Lädt User und Gruppe frisch innerhalb der Transaktion, damit Hibernate
   *  die groups-Collection korrekt als PersistentCollection trackt. */
  @WithTransaction
  public Uni<User> addToGroup(User user, Gruppe gruppe) {
    return findUserWithGroups(user.id)
      .chain(freshUser -> findGruppeById(gruppe.id)
        .chain(freshGruppe -> {
          boolean alreadyMember = freshUser.groups.stream()
              .anyMatch(g -> g.id != null && g.id.equals(freshGruppe.id));
          if (!alreadyMember) {
            freshUser.groups.add(freshGruppe);
          }
          freshUser.activeGroup = freshGruppe;
          return persistUser(freshUser);
        }));
  }

  protected Uni<User> findUserWithGroups(long userId) {
    return User.<User>find(
        "FROM User u LEFT JOIN FETCH u.groups LEFT JOIN FETCH u.activeGroup WHERE u.id = ?1", userId)
        .list()
        .map(users -> users.isEmpty() ? null : users.get(0));
  }

  protected Uni<Gruppe> findGruppeById(long groupId) {
    return Gruppe.findById(groupId);
  }

  protected Uni<User> persistUser(User user) {
    return user.persistAndFlush();
  }

  /** Wechselt die aktive Gruppe — User muss Mitglied sein. */
  @WithTransaction
  public Uni<User> setActiveGroup(User user, long groupId) {
    return findGruppeById(groupId)
      .onItem().ifNull().failWith(() -> new ClientErrorException(Response.Status.NOT_FOUND))
      .chain(gruppe -> {
        boolean isMember = user.groups.stream().anyMatch(g -> g.id != null && g.id.equals(gruppe.id));
        if (!isMember) {
          throw new ClientErrorException("User ist kein Mitglied dieser Gruppe", Response.Status.FORBIDDEN);
        }
        user.activeGroup = gruppe;
        return persistUser(user);
      });
  }

  /** Verlässt die aktive Gruppe (setzt activeGroup auf null). */
  @WithTransaction
  public Uni<User> clearActiveGroup(User user) {
    user.activeGroup = null;
    return persistUser(user);
  }
}
