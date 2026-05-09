package de.jamsintown.text;

import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.quarkus.security.ForbiddenException;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextServiceTest {

    private User user(long id) {
        User u = new User();
        u.id = id;
        u.name = "user-" + id;
        u.groups = new ArrayList<>();
        return u;
    }

    private TextService serviceWith(User currentUser) {
        return new TextService(new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(currentUser);
            }
        }) {
            @Override
            protected Uni<Text> findTextById(long id) {
                return Uni.createFrom().nullItem();
            }
            @Override
            protected Uni<Text> persistText(Text text) {
                return Uni.createFrom().item(text);
            }
        };
    }

    @Test
    void create_setztUserUndGruppe() {
        Gruppe g = new Gruppe();
        g.id = 1L;
        User u = user(1L);
        u.activeGroup = g;

        TextService svc = serviceWith(u);
        Text text = new Text();
        Text result = svc.create(text).await().indefinitely();

        assertSame(u, result.user);
        assertSame(g, result.group);
    }

    @Test
    void create_ohneGruppe_setztGroupNull() {
        User u = user(1L);
        TextService svc = serviceWith(u);
        Text text = new Text();
        Text result = svc.create(text).await().indefinitely();
        assertNull(result.group);
    }

    @Test
    void findByIdForUser_andererUser_wirftForbidden() {
        User owner = user(1L);
        User stranger = user(2L);
        Text text = new Text();
        text.id = 5L;
        text.user = owner;

        TextService svc = new TextService(new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(stranger);
            }
        }) {
            @Override
            protected Uni<Text> findTextByIdDirect(Long id) {
                return Uni.createFrom().item(text);
            }
        };

        assertThrows(ForbiddenException.class,
                () -> svc.findByIdForUser(5L).await().indefinitely());
    }

    @Test
    void findByIdForUser_eigenerText_erfolgreich() {
        User owner = user(1L);
        Text text = new Text();
        text.id = 5L;
        text.user = owner;

        TextService svc = new TextService(new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(owner);
            }
        }) {
            @Override
            protected Uni<Text> findTextByIdDirect(Long id) {
                return Uni.createFrom().item(text);
            }
        };

        Text result = svc.findByIdForUser(5L).await().indefinitely();
        assertSame(text, result);
    }

    @Test
    void listForUser_mitGruppe_filtert() {
        Gruppe g = new Gruppe();
        g.id = 1L;
        User u = user(1L);
        u.activeGroup = g;

        TextService svc = new TextService(new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(u);
            }
        }) {
            @Override
            protected Uni<List<Text>> findByGroup(Gruppe gruppe) {
                return Uni.createFrom().item(List.of());
            }
            @Override
            protected Uni<List<Text>> findByUser(User user) {
                throw new AssertionError("Sollte nicht aufgerufen werden");
            }
        };

        List<Text> result = svc.listForUser().await().indefinitely();
        assertNotNull(result);
    }
}
