package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.text.Text;
import de.jamsintown.video.Video;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.ObjectNotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class StoryService {

    private final UserService userService;

    @Inject
    public StoryService(UserService userService) {
        this.userService = userService;
    }

    public Uni<Story> findById(long id) {
        return userService.getCurrentUser()
                .chain(user -> Story.<Story>find(
                        "FROM Story s JOIN FETCH s.user u LEFT JOIN FETCH u.groups WHERE s.id = ?1", id)
                        .firstResult()
                        .onItem().ifNull().failWith(() -> new ObjectNotFoundException(id, "Story"))
                        .onItem().invoke(story -> {
                            Gruppe g = user.activeGroup;
                            boolean inGroup = g != null && story.user.groups.stream()
                                    .anyMatch(gr -> g.id != null && g.id.equals(gr.id));
                            if (!user.id.equals(story.user.id) && !inGroup) {
                                throw new UnauthorizedException("You are not allowed to update this story");
                            }
                        }));
    }

    public Uni<List<Story>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> {
                    Gruppe g = user.activeGroup;
                    if (g != null) {
                        return Story.<Story>find("group = ?1", g).list();
                    }
                    return Story.<Story>find("user = ?1 and group is null", user).list();
                });
    }

    @WithTransaction
    public Uni<Story> create(Story story) {
        return userService.getCurrentUser()
                .chain(user -> {
                    story.user = user;
                    story.group = user.activeGroup;
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
                .chain(s -> Bild.update("story = null where story = ?1", s)
                        .chain(i -> Text.update("story = null where story = ?1", s))
                        .chain(i -> s.delete()));
    }

    @WithTransaction
    public Uni<Void> deleteWithContent(long id) {
        return findById(id)
                .chain(s -> Bild.update("deleted = true, deletedFromStoryName = ?2, story = null where story = ?1", s, s.name)
                        .chain(i -> Text.update("deleted = true, deletedFromStoryName = ?2, story = null where story = ?1", s, s.name))
                        .chain(i -> s.delete()));
    }

    @WithTransaction
    public Uni<Story> restoreByName(String name, boolean withContent) {
        return userService.getCurrentUser()
                .chain(user -> {
                    Story story = new Story();
                    story.name = name;
                    story.user = user;
                    return story.<Story>persistAndFlush()
                            .chain(saved -> {
                                if (!withContent) return Uni.createFrom().item(saved);
                                return Bild.update(
                                        "deleted = false, deletedFromStoryName = null, story = ?1 where user = ?2 and deletedFromStoryName = ?3",
                                        saved, user, name)
                                    .chain(i -> Text.update(
                                        "deleted = false, deletedFromStoryName = null, story = ?1 where user = ?2 and deletedFromStoryName = ?3",
                                        saved, user, name))
                                    .replaceWith(saved);
                            });
                });
    }

    @WithTransaction
    public Uni<Void> reorder(long storyId, List<ReorderItem> items) {
        return findById(storyId).chain(story -> {
            List<Uni<?>> updates = new ArrayList<>();
            Map<Integer, Integer> colCounters = new HashMap<>();
            for (ReorderItem item : items) {
                int pos = colCounters.merge(item.column, 0, (old, x) -> old + 1);
                if ("bild".equals(item.type)) {
                    updates.add(Bild.update("storyPosition = ?1, storyColumn = ?2 where id = ?3", pos, item.column, item.id));
                } else if ("video".equals(item.type)) {
                    updates.add(Video.update("storyPosition = ?1, storyColumn = ?2 where id = ?3", pos, item.column, item.id));
                } else {
                    updates.add(Text.update("storyPosition = ?1, storyColumn = ?2 where id = ?3", pos, item.column, item.id));
                }
            }
            return Uni.combine().all().unis(updates).discardItems();
        });
    }
}
