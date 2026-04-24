package de.jamsintown.user;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für GruppeService.
 *
 * Kein Quarkus, keine Datenbank — Panache-Aufrufe werden durch Überschreiben
 * der geschützten Methoden findGruppeById() und persistUser() gestubbt.
 */
class GruppeServiceTest {

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    private Gruppe gruppeStub(long id) {
        Gruppe g = new Gruppe();
        g.id = id;
        g.name = "TestGruppe-" + id;
        return g;
    }

    private User userStub(Gruppe... memberOf) {
        User user = new User();
        user.groups = new ArrayList<>(Arrays.asList(memberOf));
        return user;
    }

    /** Service, der findGruppeById, findUserWithGroups und persistUser stubbt. */
    private GruppeService serviceWith(Gruppe findResult) {
        return serviceWith(findResult, null);
    }

    /** Service mit stubbaren Panache-Methoden. */
    private GruppeService serviceWith(Gruppe gruppeResult, User userResult) {
        return new GruppeService() {
            @Override
            protected Uni<Gruppe> findGruppeById(long id) {
                return gruppeResult != null
                        ? Uni.createFrom().item(gruppeResult)
                        : Uni.createFrom().nullItem();
            }

            @Override
            protected Uni<User> persistUser(User user) {
                return Uni.createFrom().item(user);
            }
        };
    }

    // -------------------------------------------------------------------------
    // setActiveGroup
    // -------------------------------------------------------------------------

    @Test
    void setActiveGroup_gruppeNichtGefunden_wirft404() {
        User user = userStub();
        GruppeService service = serviceWith(null);
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.setActiveGroup(user, 99L).await().indefinitely());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void setActiveGroup_userNichtMitglied_wirft403() {
        Gruppe gruppe = gruppeStub(1L);
        User user = userStub(); // kein Mitglied
        GruppeService service = serviceWith(gruppe);
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.setActiveGroup(user, 1L).await().indefinitely());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void setActiveGroup_userIstMitglied_setztAktiveGruppe() {
        Gruppe gruppe = gruppeStub(1L);
        // Zweite Instanz gleicher ID — simulates different Hibernate session instances
        Gruppe gruppeAlterInstance = gruppeStub(1L);
        User user = userStub(gruppe); // user.groups enthält erste Instanz
        GruppeService service = serviceWith(gruppeAlterInstance); // findGruppeById gibt andere Instanz zurück
        User result = service.setActiveGroup(user, 1L).await().indefinitely();
        // activeGroup wird auf die von findGruppeById zurückgegebene Instanz gesetzt
        assertSame(gruppeAlterInstance, result.activeGroup);
    }

    // -------------------------------------------------------------------------
    // clearActiveGroup
    // -------------------------------------------------------------------------

    @Test
    void clearActiveGroup_setztActiveGroupAufNull() {
        Gruppe gruppe = gruppeStub(1L);
        User user = userStub(gruppe);
        user.activeGroup = gruppe;
        GruppeService service = serviceWith(gruppe); // persistUser gestubbt
        User result = service.clearActiveGroup(user).await().indefinitely();
        assertNull(result.activeGroup);
    }

}
