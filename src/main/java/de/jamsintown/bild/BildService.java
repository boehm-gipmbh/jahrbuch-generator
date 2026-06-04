package de.jamsintown.bild;

import de.jamsintown.user.Gruppe;
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
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    if (g != null) {
                        return Bild.<Bild>find("group = ?1 and deleted = false", g).list();
                    }
                    return Bild.<Bild>find("user = ?1 and group is null and deleted = false", user).list();
                });
    }

    public Uni<List<Bild>> listDeleted() {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    if (g != null) {
                        return Bild.<Bild>find("group = ?1 and deleted = true", g).list();
                    }
                    return Bild.<Bild>find("user = ?1 and group is null and deleted = true", user).list();
                });
    }

    public Uni<Bild> findByPfad(String pfad) {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    Uni<Bild> query;
                    if (g != null) {
                        query = Bild.<Bild>find("pfad = ?1 and (user = ?2 or group = ?3)", pfad, user, g).firstResult();
                    } else {
                        query = Bild.<Bild>find("pfad = ?1 and user = ?2", pfad, user).firstResult();
                    }
                    return query.onItem().ifNull().failWith(
                            () -> new ObjectNotFoundException((java.io.Serializable) pfad, "Bild"));
                });
    }

    public Uni<Bild> findById(Long id) {
        return userService.getCurrentUser()
                .chain(user -> Bild.<Bild>find(
                        "FROM Bild b JOIN FETCH b.user u WHERE b.id = ?1", id)
                        .firstResult()
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Bild"))
                        .onItem().invoke(bild -> {
                            Gruppe g = user.activeGroup;
                            boolean inGroup = g != null && bild.group != null && g.id.equals(bild.group.id);
                            if (!user.id.equals(bild.getUser().id) && !inGroup) {
                                throw new ForbiddenException("Access denied to bild with id: " + id);
                            }
                        }));
    }

    @WithTransaction
    public Uni<Bild> create(Bild bild) {
        return userService.getCurrentUser()
                .chain(user -> {
                    bild.user = user;
                    bild.group = user.activeGroup;
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
    public Uni<Void> clearCaption(long id) {
        return findById(id)
                .chain(bild -> {
                    bild.caption = null;
                    return Bild.getSession().chain(s -> s.merge(bild));
                })
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Integer> clearAllCaptions() {
        return Bild.update("caption = null where caption is not null");
    }

    @WithTransaction
    public Uni<Integer> backfillCapturedAt(List<Bild> bilder) {
        if (bilder.isEmpty()) return Uni.createFrom().item(0);
        return Bild.getSession()
                .chain(s -> {
                    List<Uni<Bild>> merges = bilder.stream()
                            .map(b -> (Uni<Bild>) s.merge(b))
                            .collect(Collectors.toList());
                    return Uni.join().all(merges).andFailFast();
                })
                .map(List::size);
    }

    public Uni<List<Bild>> listWithoutCapturedAt() {
        return Bild.<Bild>find("capturedAt is null and deleted = false").list();
    }


    @WithTransaction
    public Uni<Void> softDelete(long id) {
        return findById(id)
                .chain(bild -> {
                    bild.deletedFromStoryName = bild.story != null ? bild.story.name : null;
                    bild.deleted = true;
                    bild.story = null;
                    return bild.persistAndFlush().replaceWithVoid();
                });
    }

    @WithTransaction
    public Uni<Bild> restore(long id) {
        return userService.getCurrentUser()
                .chain(user -> findByIdIncludeDeleted(id)
                        .chain(bild -> {
                            bild.deleted = false;
                            String storyName = bild.deletedFromStoryName;
                            bild.deletedFromStoryName = null;
                            if (storyName == null) {
                                return bild.persistAndFlush();
                            }
                            return de.jamsintown.story.Story
                                    .<de.jamsintown.story.Story>find("user = ?1 and name = ?2", user, storyName)
                                    .firstResult()
                                    .chain(story -> {
                                        bild.story = story; // null wenn nicht gefunden → bleibt unzugeordnet
                                        return bild.persistAndFlush();
                                    });
                        }));
    }

    @WithTransaction
    public Uni<Void> hardDelete(long id) {
        return findByIdIncludeDeleted(id)
                .chain(bild -> {
                    String fullPath = capturesPath + bild.pfad.replaceFirst("^/", "");
                    try {
                        Files.deleteIfExists(Paths.get(fullPath));
                        String fileName = Paths.get(fullPath).getFileName().toString();
                        Files.deleteIfExists(Paths.get(capturesPath).resolve(BilderUploadResource.toThumbName(fileName)));
                    } catch (IOException e) {
                        return Uni.<Void>createFrom().failure(new RuntimeException("Fehler beim Löschen der Datei: " + e.getMessage(), e));
                    }
                    return bild.delete();
                });
    }

    protected Uni<Bild> findByIdIncludeDeleted(Long id) {
        return userService.getCurrentUser()
                .chain(user -> Bild.<Bild>find(
                        "FROM Bild b JOIN FETCH b.user u WHERE b.id = ?1", id)
                        .firstResult()
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Bild"))
                        .onItem().invoke(bild -> {
                            Gruppe g = user.activeGroup;
                            boolean inGroup = g != null && bild.group != null && g.id.equals(bild.group.id);
                            if (!user.id.equals(bild.getUser().id) && !inGroup) {
                                throw new ForbiddenException("Access denied to bild with id: " + id);
                            }
                        }));
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

    @WithTransaction
    public Uni<Boolean> setHauptbild(long id, boolean hauptbild) {
        return findById(id)
                .chain(bild -> {
                    bild.hauptbild = hauptbild;
                    return bild.persistAndFlush();
                })
                .chain(bild -> Uni.createFrom().item(hauptbild));
    }

}
