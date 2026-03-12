package de.jamsintown.bild;

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
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BildService {

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    private String capturesPath;

    private final UserService userService;

    @Inject
    public BildService(UserService userService) {
        this.userService = userService;
    }

    public Uni<List<Bild>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> Bild.find("user", user).list());
    }

    public Uni<Bild> findByPfad(String pfad) {
        return userService.getCurrentUser()
                .chain(user -> Bild.<Bild>find("pfad = ?1 and user = ?2", pfad, user).firstResult()
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException((java.io.Serializable) pfad, "Bild")));
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
    public Uni<Bild> bumpVersion(Long id) {
        return Bild.update("version = version + 1 where id = ?1", id)
                .chain(() -> findById(id));
    }

@WithTransaction
public Uni<Void> delete(long id) {
    // Einmaliger DB-Lookup: Bild laden, Datei von Festplatte löschen, dann aus DB löschen
    return findById(id)
            .chain(bild -> {
                String fullPath = capturesPath + bild.pfad.replaceFirst("^/", "");
                try {
                    Files.deleteIfExists(Paths.get(fullPath));
                    String fileName = Paths.get(fullPath).getFileName().toString();
                    Files.deleteIfExists(Paths.get(capturesPath).resolve(de.jamsintown.bild.BilderUploadResource.toThumbName(fileName)));
                } catch (IOException e) {
                    return Uni.<Void>createFrom().failure(new RuntimeException("Fehler beim Löschen der Datei: " + e.getMessage(), e));
                }
                return bild.delete();
            });
}

    @WithTransaction
    public Uni<List<Bild>> reorder(Long storyId, List<Long> bildIds) {
        return userService.getCurrentUser()
                .chain(user -> Bild.<Bild>find("story.id = ?1 and user = ?2", storyId, user).list()
                        .chain(bilder -> {
                            Map<Long, Bild> byId = bilder.stream()
                                    .collect(Collectors.toMap(b -> b.id, b -> b));
                            for (int i = 0; i < bildIds.size(); i++) {
                                Bild b = byId.get(bildIds.get(i));
                                if (b != null) {
                                    b.position = i;
                                }
                            }
                            return Bild.getSession()
                                    .chain(s -> s.flush())
                                    .replaceWith(bilder);
                        }));
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

}
