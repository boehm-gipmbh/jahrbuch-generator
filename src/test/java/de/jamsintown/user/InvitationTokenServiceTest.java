package de.jamsintown.user;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für InvitationTokenService.
 *
 * Kein Quarkus, keine Datenbank — Panache-Aufrufe werden durch Überschreiben
 * der geschützten Hilfsmethoden (findByToken, findUserByName) gestubbt.
 * Getestet wird ausschließlich die Geschäftslogik: Token-Validierung und
 * die Fehlerfälle beim Registrieren.
 *
 * Der Happy-Path von register() (User in DB anlegen) ist ein @QuarkusTest,
 * weil persistAndFlush() eine echte Hibernate-Session erfordert.
 */
class InvitationTokenServiceTest {

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    /** Baut einen Service, der findByToken mit dem gegebenen Token antwortet. */
    private InvitationTokenService serviceWith(InvitationToken token) {
        return new InvitationTokenService(null, null) {
            @Override
            protected Uni<InvitationToken> findByToken(UUID tokenValue) {
                return Uni.createFrom().item(token);
            }
        };
    }

    /** Baut einen Service, der findByToken mit null antwortet (Token nicht gefunden). */
    private InvitationTokenService serviceWithNotFound() {
        return new InvitationTokenService(null, null) {
            @Override
            protected Uni<InvitationToken> findByToken(UUID tokenValue) {
                return Uni.createFrom().nullItem();
            }
        };
    }

    private InvitationToken validToken() {
        InvitationToken t = new InvitationToken();
        t.token = UUID.randomUUID();
        t.role = "user";
        t.active = true;
        t.expiresAt = ZonedDateTime.now().plusDays(7);
        return t;
    }

    private void assertGone(ClientErrorException ex) {
        assertEquals(Response.Status.GONE.getStatusCode(), ex.getResponse().getStatus());
    }

    // -------------------------------------------------------------------------
    // validate()
    // -------------------------------------------------------------------------

    @Test
    void validate_gueltigesToken_wirdZurueckgegeben() {
        InvitationToken token = validToken();
        InvitationToken result = serviceWith(token).validate(token.token).await().indefinitely();
        assertSame(token, result);
    }

    @Test
    void validate_tokenNichtGefunden_wirft410() {
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> serviceWithNotFound().validate(UUID.randomUUID()).await().indefinitely());
        assertGone(ex);
    }

    @Test
    void validate_tokenInaktiv_wirft410() {
        InvitationToken token = validToken();
        token.active = false;
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> serviceWith(token).validate(token.token).await().indefinitely());
        assertGone(ex);
    }

    @Test
    void validate_tokenAbgelaufen_wirft410() {
        InvitationToken token = validToken();
        token.expiresAt = ZonedDateTime.now().minusMinutes(1);
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> serviceWith(token).validate(token.token).await().indefinitely());
        assertGone(ex);
    }

    @Test
    void validate_tokenGeradeNochGueltig_wirdAkzeptiert() {
        InvitationToken token = validToken();
        token.expiresAt = ZonedDateTime.now().plusSeconds(5);
        InvitationToken result = serviceWith(token).validate(token.token).await().indefinitely();
        assertSame(token, result);
    }

    // -------------------------------------------------------------------------
    // register() — nur Fehlerfälle (Happy-Path braucht DB → @QuarkusTest)
    // -------------------------------------------------------------------------

    @Test
    void register_tokenNichtGefunden_wirft410() {
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> serviceWithNotFound()
                        .register(UUID.randomUUID(), "Max", "max@test.de", "pw")
                        .await().indefinitely());
        assertGone(ex);
    }

    @Test
    void register_tokenInaktiv_wirft410() {
        InvitationToken token = validToken();
        token.active = false;
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> serviceWith(token)
                        .register(token.token, "Max", "max@test.de", "pw")
                        .await().indefinitely());
        assertGone(ex);
    }

    @Test
    void register_tokenAbgelaufen_wirft410() {
        InvitationToken token = validToken();
        token.expiresAt = ZonedDateTime.now().minusDays(1);
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> serviceWith(token)
                        .register(token.token, "Max", "max@test.de", "pw")
                        .await().indefinitely());
        assertGone(ex);
    }
}