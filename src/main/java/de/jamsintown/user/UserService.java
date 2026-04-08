package de.jamsintown.user;

import de.jamsintown.bild.Bild;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hibernate.ObjectNotFoundException;

import java.util.List;

@ApplicationScoped
public class UserService {

  private final JsonWebToken jwt;

  @Inject
  public UserService(JsonWebToken jwt) {
    this.jwt = jwt;
  }

  public Uni<User> findById(long id) {
    return User.<User>findById(id)
      .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "User"));
  }

  public Uni<User> findByName(String name) {
    return User.<User>find("FROM User u LEFT JOIN FETCH u.groups WHERE u.name = ?1", name).firstResult();
  }

  public Uni<List<User>> list() {
    return User.listAll();
  }

  @WithTransaction
  public Uni<User> create(User user) {
    user.password = BcryptUtil.bcryptHash(user.password);
    return user.persistAndFlush();
  }

  @WithTransaction
  public Uni<User> update(User user) {
    return findById(user.id).chain(u -> {
        user.setPassword(u.password);
        return User.getSession();
      })
      .chain(s -> s.merge(user));
  }

  public Uni<User> getCurrentUser() {
    return findByName(jwt.getName());
  }

  public static boolean matches(User user, String password) {
    return BcryptUtil.matches(password, user.password);
  }

  @WithTransaction
  public Uni<Void> delete(long id) {
    return findById(id)
        .chain(u -> de.jamsintown.bild.Bild.count("user", u)
            .flatMap(bilder -> de.jamsintown.text.Text.count("user", u)
                .flatMap(texte -> de.jamsintown.story.Story.count("user", u)
                    .chain(stories -> {
                        if (bilder > 0 || texte > 0 || stories > 0) {
                            throw new ClientErrorException(
                                "User hat noch Inhalte (" + bilder + " Bilder, " + texte + " Texte, " + stories + " Stories). Bitte zuerst deaktivieren.",
                                Response.Status.CONFLICT);
                        }
                        return u.delete();
                    }))));
  }

  @WithTransaction
  public Uni<User> deactivate(long id) {
    return findById(id).chain(u -> {
      u.active = false;
      return u.persistAndFlush();
    });
  }

  @WithTransaction
  public Uni<User> reactivate(long id) {
    return findById(id).chain(u -> {
      u.active = true;
      return u.persistAndFlush();
    });
  }

  @WithTransaction
  public Uni<User> changePassword(String currentPassword, String newPassword) {
    return getCurrentUser()
      .chain(u -> {
        if (!matches(u, currentPassword)) {
          throw new ClientErrorException("Current password does not match", Response.Status.CONFLICT);
        }
        u.setPassword(BcryptUtil.bcryptHash(newPassword));
        return u.persistAndFlush();
      });
  }

}
