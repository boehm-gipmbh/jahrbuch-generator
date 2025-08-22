package de.jamsintown.bild;

import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.ForbiddenException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.ObjectNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

import java.time.ZonedDateTime;
import java.util.List;

@ApplicationScoped
public class BildService {

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    private String capturesPath;

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
    public Uni<Bild> update(Bild bild) {
        return findById(bild.id)
                .chain(t -> Bild.getSession())
                .chain(s -> s.merge(bild));
    }

@WithTransaction
public Uni<Void> delete(long id) {
    return findFullPathById(id)
            .chain(fullPath -> {
                try {
                    // Datei von der Festplatte löschen
                    Files.deleteIfExists(Paths.get(fullPath));
                    return Uni.createFrom().item(fullPath);
                } catch (IOException e) {
                    return Uni.createFrom().failure(new RuntimeException("Fehler beim Löschen der Datei: " + e.getMessage(), e));
                }
            })
            .chain(path -> findById(id))
            .chain(Bild::delete);
}

    @WithTransaction
    public Uni<Boolean> setComplete(long id, boolean complete) {
        return findById(id)
                .chain(bild -> {
                    bild.complete = complete ? ZonedDateTime.now() : null;
                    return bild.persistAndFlush();
                })
                .chain(bild -> Uni.createFrom().item(complete));
    }

    /**
     * Ruft nur den Dateipfad eines Bildes anhand seiner ID ab
     *
     * @param id Die ID des Bildes
     * @return Uni mit dem Pfad des Bildes oder Fehler, wenn nicht gefunden
     */
    private Uni<String> findFullPathById(Long id) {
        return findById(id)
                .map(bild -> capturesPath + bild.pfad.replaceFirst("^/", ""));
    }

    public void rotateBild(String string, int degrees) {
    }
}
