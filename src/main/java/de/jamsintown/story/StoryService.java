package de.jamsintown.story;

import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.ObjectNotFoundException;

import java.util.List;

@ApplicationScoped
public class StoryService {

    private final UserService userService;

    @Inject
    public StoryService(UserService userService) {
        this.userService = userService;
    }

    public Uni<Story> findById(long id) {
        return userService.getCurrentUser()
                .chain(user -> Story.<Story>findById(id)
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Story"))
                        .onItem().invoke(story -> {
                            if (!user.equals(story.user)) {
                                throw new UnauthorizedException("You are not allowed to update this story");
                            }
                        }));
    }

    public Uni<List<Story>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> Story.find("user", user).list());
    }

    @WithTransaction
    public Uni<Story> create(Story story) {
        return userService.getCurrentUser()
                .chain(user -> {
                    story.user = user;
                    return story.persistAndFlush();
                });
    }

    @WithTransaction
    public Uni<Story> update(Story story) {
        return findById(story.id)
                .chain(p -> Story.getSession())
                .chain(s -> s.merge(story));
    }

    @WithTransaction
    public Uni<Void> delete(long id) {
        return findById(id)
                .chain(s -> Story.update("story = null where story = ?1", s)
                        .chain(i -> s.delete()));
    }
}
