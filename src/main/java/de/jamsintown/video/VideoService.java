package de.jamsintown.video;

import de.jamsintown.user.Gruppe;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.ForbiddenException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.ObjectNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class VideoService {

    private final UserService userService;

    @Inject
    public VideoService(UserService userService) {
        this.userService = userService;
    }

    public Uni<List<Video>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    if (g != null) {
                        return Video.<Video>find(
                                "SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.story WHERE v.group = ?1 AND v.deleted = false", g).list();
                    }
                    return Video.<Video>find(
                            "SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.story WHERE v.user = ?1 AND v.group IS NULL AND v.deleted = false", user).list();
                });
    }

    public Uni<Video> findById(Long id) {
        return userService.getCurrentUser()
                .chain(user -> Video.<Video>find(
                        "FROM Video v JOIN FETCH v.user u LEFT JOIN FETCH u.groups WHERE v.id = ?1", id)
                        .firstResult()
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Video"))
                        .onItem().invoke(video -> {
                            Gruppe g = user.activeGroup;
                            boolean inGroup = g != null && video.user.groups.stream()
                                    .anyMatch(gr -> g.id != null && g.id.equals(gr.id));
                            if (!user.id.equals(video.user.id) && !inGroup) {
                                throw new ForbiddenException("Access denied to video with id: " + id);
                            }
                        }));
    }

    public Uni<Video> findByPfad(String pfad) {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    Uni<Video> query;
                    if (g != null) {
                        query = Video.<Video>find("pfad = ?1 and (user = ?2 or group = ?3)", pfad, user, g).firstResult();
                    } else {
                        query = Video.<Video>find("pfad = ?1 and user = ?2", pfad, user).firstResult();
                    }
                    return query.onItem().ifNull().failWith(
                            () -> new ObjectNotFoundException((java.io.Serializable) pfad, "Video"));
                });
    }

    @WithTransaction
    public Uni<Video> create(Video video) {
        return userService.getCurrentUser()
                .chain(user -> {
                    video.user = user;
                    video.group = user.activeGroup;
                    return video.persistAndFlush();
                });
    }

    @WithTransaction
    public Uni<Video> update(Video incoming) {
        return findById(incoming.id)
                .chain(video -> Video.getSession())
                .chain(s -> s.merge(incoming));
    }

    @WithTransaction
    public Uni<Boolean> setComplete(long id, boolean complete) {
        return findById(id)
                .chain(video -> {
                    video.complete = complete;
                    return video.persistAndFlush().map(v -> ((Video) v).complete);
                });
    }

    public Uni<List<Video>> listDeleted() {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    if (g != null) {
                        return Video.<Video>find(
                                "SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.story WHERE v.group = ?1 AND v.deleted = true", g).list();
                    }
                    return Video.<Video>find(
                            "SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.story WHERE v.user = ?1 AND v.group IS NULL AND v.deleted = true", user).list();
                });
    }

    @WithTransaction
    public Uni<Void> hardDelete(long id) {
        return findByIdIncludeDeleted(id)
                .chain(video -> video.delete().replaceWithVoid());
    }

    @WithTransaction
    public Uni<Void> softDelete(long id) {
        return findById(id)
                .chain(video -> {
                    video.deleted = true;
                    video.story = null;
                    return video.persistAndFlush().replaceWithVoid();
                });
    }

    @WithTransaction
    public Uni<Integer> backfillCapturedAt(List<Video> videos) {
        if (videos.isEmpty()) return Uni.createFrom().item(0);
        return Video.getSession()
                .chain(s -> {
                    List<Uni<Video>> merges = videos.stream()
                            .map(v -> (Uni<Video>) s.merge(v))
                            .collect(Collectors.toList());
                    return Uni.join().all(merges).andFailFast();
                })
                .map(List::size);
    }

    public Uni<List<Video>> listWithoutCapturedAt() {
        return Video.<Video>find("capturedAt is null and deleted = false").list();
    }

    @WithTransaction
    public Uni<Video> restore(long id) {
        return findByIdIncludeDeleted(id)
                .chain(video -> {
                    video.deleted = false;
                    return video.persistAndFlush();
                });
    }

    protected Uni<Video> findByIdIncludeDeleted(Long id) {
        return userService.getCurrentUser()
                .chain(user -> Video.<Video>find(
                        "FROM Video v JOIN FETCH v.user u LEFT JOIN FETCH u.groups WHERE v.id = ?1", id)
                        .firstResult()
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Video"))
                        .onItem().invoke(video -> {
                            Gruppe g = user.activeGroup;
                            boolean inGroup = g != null && video.user.groups.stream()
                                    .anyMatch(gr -> g.id != null && g.id.equals(gr.id));
                            if (!user.id.equals(video.user.id) && !inGroup) {
                                throw new ForbiddenException("Access denied to video with id: " + id);
                            }
                        }));
    }
}