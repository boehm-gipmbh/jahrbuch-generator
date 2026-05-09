package de.jamsintown.fotobox;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FotoboxTokenServiceTest {

    private FotoboxToken stored;

    private FotoboxTokenService service() {
        stored = null;
        return new FotoboxTokenService() {
            {
                issuer = "test-issuer";
            }

            @Override
            protected Uni<FotoboxToken> findByGroupId(long groupId) {
                return Uni.createFrom().item(stored);
            }

            @Override
            protected Uni<FotoboxToken> persist(FotoboxToken token) {
                stored = token;
                return Uni.createFrom().item(token);
            }
        };
    }

    @BeforeEach
    void reset() {
        stored = null;
    }

    @Test
    void generateToken_speichertJtiInDb() {
        FotoboxTokenService svc = service();
        svc.generateToken(1L, LocalDate.now(), LocalDate.now().plusDays(7)).await().indefinitely();
        assertNotNull(stored);
        assertNotNull(stored.jti);
        assertEquals(1L, stored.groupId);
        assertFalse(stored.revoked);
    }

    @Test
    void generateToken_ueberschreibtVorhandenenRecord() {
        FotoboxTokenService svc = service();
        FotoboxToken existing = new FotoboxToken();
        existing.groupId = 1L;
        existing.jti = UUID.randomUUID();
        existing.revoked = false;
        stored = existing;

        svc.generateToken(1L, LocalDate.now(), LocalDate.now().plusDays(7)).await().indefinitely();

        assertSame(existing, stored);
        assertFalse(stored.revoked);
    }

    @Test
    void isValid_gueltigerJti_gibtTrue() {
        FotoboxTokenService svc = service();
        UUID jti = UUID.randomUUID();
        stored = tokenWith(jti, false);

        boolean result = svc.isValid(1L, jti.toString()).await().indefinitely();
        assertTrue(result);
    }

    @Test
    void isValid_widerrufenerToken_gibtFalse() {
        FotoboxTokenService svc = service();
        UUID jti = UUID.randomUUID();
        stored = tokenWith(jti, true);

        boolean result = svc.isValid(1L, jti.toString()).await().indefinitely();
        assertFalse(result);
    }

    @Test
    void isValid_falscherJti_gibtFalse() {
        FotoboxTokenService svc = service();
        stored = tokenWith(UUID.randomUUID(), false);

        boolean result = svc.isValid(1L, UUID.randomUUID().toString()).await().indefinitely();
        assertFalse(result);
    }

    @Test
    void isValid_keinRecord_gibtFalse() {
        FotoboxTokenService svc = service();
        // stored bleibt null

        boolean result = svc.isValid(1L, UUID.randomUUID().toString()).await().indefinitely();
        assertFalse(result);
    }

    @Test
    void revokeToken_setztRevokedAufTrue() {
        FotoboxTokenService svc = service();
        UUID jti = UUID.randomUUID();
        stored = tokenWith(jti, false);

        svc.revokeToken(1L).await().indefinitely();

        assertTrue(stored.revoked);
    }

    @Test
    void revokeToken_keinRecord_keinFehler() {
        FotoboxTokenService svc = service();
        // stored bleibt null — darf nicht werfen
        assertDoesNotThrow(() -> svc.revokeToken(1L).await().indefinitely());
    }

    private FotoboxToken tokenWith(UUID jti, boolean revoked) {
        FotoboxToken t = new FotoboxToken();
        t.groupId = 1L;
        t.jti = jti;
        t.revoked = revoked;
        return t;
    }
}
