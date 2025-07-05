package de.jamsintown.story;

import de.jamsintown.bild.BildService;
import de.jamsintown.text.TextService;
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
    private final BildService bildService;
    private final TextService textService;

    @Inject
    public StoryService(UserService userService, BildService bildService, TextService textService) {
        this.userService = userService;
        this.bildService = bildService;
        this.textService = textService;
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
//        public Uni<List<Story>> listForUser() {
//            return userService.getCurrentUser()
//                    .chain(user -> Story.find("user", user).list())
//                    .chain(stories -> Uni.combine().all().unis(
//                            stories.stream().map(story ->
//                                Uni.combine().all().unis(
//                                    textService.,
//                                    bildService.findByStoryId(story.id)
//                                ).asTuple().invoke(tuple -> {
//                                    story.text = tuple.getItem1();
//                                    story.bild = tuple.getItem2();
//                                })
//                            ).concatenate().collect().asList()
//                    ).replaceWith(stories));
//        }
//
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

//    @WithTransaction
//    public Uni<Void> delete(long id) {
//        return findById(id)
//                .chain(p -> Task.update("project = null where project = ?1", p)
//                        .chain(i -> p.delete()));
//    }
}
