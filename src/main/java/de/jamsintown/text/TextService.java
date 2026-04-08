package de.jamsintown.text;

import de.jamsintown.user.Gruppe;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.ObjectNotFoundException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class TextService {
    private final UserService userService;

    @Inject
    public TextService(UserService userService) {
        this.userService = userService;
    }

    private Uni<Text> findById(long id) {
        return userService.getCurrentUser()
                .chain(user -> Text.<Text>findById(id)
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Text"))
                        .onItem().invoke(text -> {
                            Gruppe g = user.activeGroup;
                            boolean inGroup = g != null && text.user.groups.contains(g);
                            if (!user.equals(text.user) && !inGroup) {
                                throw new UnauthorizedException("You are not allowed to update this text");
                            }
                        }));
    }

    public Uni<List<Text>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    if (g != null) {
                        return Text.<Text>find(
                            "user.id IN (SELECT u.id FROM User u JOIN u.groups gr WHERE gr = ?1) AND deleted = false", g).list();
                    }
                    return Text.<Text>find("user = ?1 and deleted = false", user).list();
                });
    }

    public Uni<List<Text>> listDeleted() {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    if (g != null) {
                        return Text.<Text>find(
                            "user.id IN (SELECT u.id FROM User u JOIN u.groups gr WHERE gr = ?1) AND deleted = true", g).list();
                    }
                    return Text.<Text>find("user = ?1 and deleted = true", user).list();
                });
    }

    public Uni<List<Text>> listAll() {
        return Text.listAll(Sort.by("created").descending());
    }

    @WithTransaction
    public Uni<Text> create(Text text) {
        return userService.getCurrentUser()
                .chain(user -> {
                    text.user = user;
                    return text.persistAndFlush();
                });
    }

    public Uni<Text> findByIdForUser(Long id) {
        return userService.getCurrentUser()
                .chain(user -> Text.<Text>findById(id)
                        .onItem().ifNull().failWith(() -> new ForbiddenException("Access denied to text with id: " + id))
                        .onItem().invoke(text -> {
                            if (!user.equals(text.user)) {
                                throw new ForbiddenException("Access denied to text with id: " + id);
                            }
                        }));
    }

    @WithTransaction
    public Uni<Text> update(Text text) {
        return findById(text.id)
                .chain(t -> Text.getSession())
                .chain(s -> s.merge(text));
    }

    @WithTransaction
    public Uni<Void> softDelete(long id) {
        return findById(id)
                .chain(text -> {
                    text.deletedFromStoryName = text.story != null ? text.story.name : null;
                    text.deleted = true;
                    text.story = null;
                    return text.persistAndFlush().replaceWithVoid();
                });
    }

    @WithTransaction
    public Uni<Text> restore(long id) {
        return userService.getCurrentUser()
                .chain(user -> findByIdIncludeDeleted(id)
                        .chain(text -> {
                            text.deleted = false;
                            String storyName = text.deletedFromStoryName;
                            text.deletedFromStoryName = null;
                            if (storyName == null) {
                                return text.persistAndFlush();
                            }
                            return de.jamsintown.story.Story
                                    .<de.jamsintown.story.Story>find("user = ?1 and name = ?2", user, storyName)
                                    .firstResult()
                                    .chain(story -> {
                                        text.story = story; // null wenn nicht gefunden → bleibt unzugeordnet
                                        return text.persistAndFlush();
                                    });
                        }));
    }

    @WithTransaction
    public Uni<Void> hardDelete(long id) {
        return findByIdIncludeDeleted(id)
                .chain(Text::delete);
    }

    private Uni<Text> findByIdIncludeDeleted(long id) {
        return userService.getCurrentUser()
                .chain(user -> Text.<Text>findById(id)
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Text"))
                        .onItem().invoke(text -> {
                            if (!user.equals(text.user)) {
                                throw new ForbiddenException("Access denied to text with id: " + id);
                            }
                        }));
    }

    @WithTransaction
    public Uni<List<Text>> reorder(Long storyId, List<Long> textIds) {
        return userService.getCurrentUser()
                .chain(user -> Text.<Text>find("story.id = ?1 and user = ?2", storyId, user).list()
                        .chain(texte -> {
                            Map<Long, Text> byId = texte.stream()
                                    .collect(Collectors.toMap(t -> t.id, t -> t));
                            for (int i = 0; i < textIds.size(); i++) {
                                Text t = byId.get(textIds.get(i));
                                if (t != null) {
                                    t.position = i;
                                }
                            }
                            return Text.getSession()
                                    .chain(s -> s.flush())
                                    .replaceWith(texte);
                        }));
    }

    @WithTransaction
    public Uni<Boolean> setComplete(long id, boolean complete) {
        return findById(id)
                .chain(text -> {
                    text.complete = complete ? ZonedDateTime.now() : null;
                    return text.persistAndFlush();
                })
                .chain(task -> Uni.createFrom().item(complete));
    }

}
