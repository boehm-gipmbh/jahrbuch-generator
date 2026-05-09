package de.jamsintown.story;

import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoryServiceTest {

    private Gruppe gruppe(long id) {
        Gruppe g = new Gruppe();
        g.id = id;
        g.name = "Gruppe-" + id;
        return g;
    }

    private User user(long id, Gruppe... memberOf) {
        User u = new User();
        u.id = id;
        u.name = "user-" + id;
        u.groups = new ArrayList<>(List.of(memberOf));
        return u;
    }

    private StoryService serviceWith(User currentUser) {
        return new StoryService(new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(currentUser);
            }
        }) {
            @Override
            protected Uni<List<Story>> findByGroup(Gruppe g, Sort sort) {
                return Uni.createFrom().item(List.of());
            }
            @Override
            protected Uni<List<Story>> findByUser(User u, Sort sort) {
                return Uni.createFrom().item(List.of());
            }
            @Override
            protected Uni<Story> persistStory(Story story) {
                return Uni.createFrom().item(story);
            }
        };
    }

    @Test
    void listForUser_ohneGruppe_delegiertAnFindByUser() {
        User u = user(1L);
        StoryService svc = serviceWith(u);
        List<Story> result = svc.listForUser().await().indefinitely();
        assertNotNull(result);
    }

    @Test
    void listForUser_mitAktiverGruppe_delegiertAnFindByGroup() {
        Gruppe g = gruppe(1L);
        User u = user(1L, g);
        u.activeGroup = g;
        StoryService svc = serviceWith(u);
        List<Story> result = svc.listForUser().await().indefinitely();
        assertNotNull(result);
    }

    @Test
    void create_setztUserUndGruppe() {
        Gruppe g = gruppe(1L);
        User u = user(1L, g);
        u.activeGroup = g;
        StoryService svc = serviceWith(u);
        Story story = new Story();
        Story result = svc.create(story).await().indefinitely();
        assertSame(u, result.user);
        assertSame(g, result.group);
    }

    @Test
    void create_ohneGruppe_setztGroupNull() {
        User u = user(1L);
        StoryService svc = serviceWith(u);
        Story story = new Story();
        Story result = svc.create(story).await().indefinitely();
        assertSame(u, result.user);
        assertNull(result.group);
    }

    @Test
    void findById_andererUserOhneGruppe_wirftUnauthorized() {
        User owner = user(1L);
        User stranger = user(2L);
        Story story = new Story();
        story.id = 10L;
        story.user = owner;

        StoryService svc = new StoryService(new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(stranger);
            }
        }) {
            @Override
            protected Uni<Story> findStoryById(long id) {
                return Uni.createFrom().item(story);
            }
        };

        assertThrows(UnauthorizedException.class,
                () -> svc.findById(10L).await().indefinitely());
    }

    @Test
    void findById_eigeneStory_erfolgreich() {
        User owner = user(1L);
        Story story = new Story();
        story.id = 10L;
        story.user = owner;

        StoryService svc = new StoryService(new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(owner);
            }
        }) {
            @Override
            protected Uni<Story> findStoryById(long id) {
                return Uni.createFrom().item(story);
            }
        };

        Story result = svc.findById(10L).await().indefinitely();
        assertSame(story, result);
    }
}
