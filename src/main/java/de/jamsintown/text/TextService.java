package de.jamsintown.text;

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
                            if (!user.equals(text.user)) {
                                throw new UnauthorizedException("You are not allowed to update this text");
                            }
                        }));
    }

    public Uni<List<Text>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> Text.find("user", user).list());
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
    public Uni<Void> delete(long id) {
        return findById(id)
                .chain(Text::delete);
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
