package de.jamsintown.bild;

import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.ForbiddenException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.ObjectNotFoundException;

import java.time.ZonedDateTime;
import java.util.List;

@ApplicationScoped
public class BildService {

    private final UserService userService;

    @Inject
    public BildService(UserService userService) {
        this.userService = userService;
    }

    public void takeBild(ImageSettings imageSettings) {
    }

    public Uni<List<Bild>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> Bild.find("user", user).list());
    }

    public Uni<Bild> findById(Long id) {
        return userService.getCurrentUser()
                .chain(user -> Bild.<Bild>findById(id)
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Bild"))
                        .onItem().invoke(bild -> {
                            if (!user.equals(bild.user)) {
                                throw new ForbiddenException("Access denied to bild with id: " + id);
                            }
                        }));
    }

    @WithTransaction
    public Uni<Bild> create(Bild bild) {
        return userService.getCurrentUser()
                .chain(user -> {
                    bild.user = user;
                    return bild.persistAndFlush();
                });
    }

    @WithTransaction
    public Uni<Boolean> setProtect(long id, boolean protect) {
        return findById(id)
                .chain(bild -> {
                    bild.protect = protect ? ZonedDateTime.now() : null;
                    return bild.persistAndFlush();
                })
                .chain(bild -> Uni.createFrom().item(protect));
    }
}
