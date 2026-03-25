package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.bild.BilderUploadResource;
import de.jamsintown.text.Text;
import de.jamsintown.user.UserService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    private String capturesPath;

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

    @WithTransaction
    public Uni<Void> deleteWithContent(long id) {
        return findById(id)
                .chain(s -> Bild.<Bild>find("story", s).list()
                        .chain(bilder -> {
                            for (Bild bild : bilder) {
                                String fullPath = capturesPath + bild.pfad.replaceFirst("^/", "");
                                try {
                                    Files.deleteIfExists(Paths.get(fullPath));
                                    String fileName = Paths.get(fullPath).getFileName().toString();
                                    Files.deleteIfExists(Paths.get(capturesPath).resolve(BilderUploadResource.toThumbName(fileName)));
                                } catch (IOException e) {
                                    // best effort, continue
                                }
                            }
                            return Bild.delete("story", s)
                                    .chain(i -> Text.delete("story", s))
                                    .chain(i -> s.delete());
                        }));
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
                } else {
                    updates.add(Text.update("storyPosition = ?1, storyColumn = ?2 where id = ?3", pos, item.column, item.id));
                }
            }
            return Uni.combine().all().unis(updates).discardItems();
        });
    }
}
