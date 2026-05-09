package de.jamsintown.auth;

import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetServiceTest {

    private final AtomicBoolean emailSent = new AtomicBoolean(false);
    private final AtomicReference<String> persistedPassword = new AtomicReference<>();

    private PasswordResetService service(User findResult, PasswordResetToken tokenResult) {
        UserService userService = new UserService(null, null) {
            @Override
            public Uni<User> findByEmail(String email) {
                return Uni.createFrom().item(findResult);
            }
        };
        PasswordResetEmailService emailService = new PasswordResetEmailService("http://test", "key", true) {
            @Override
            public void sendResetMail(String email, String name, String token) {
                emailSent.set(true);
            }
        };
        return new PasswordResetService(userService, emailService) {
            @Override
            protected Uni<PasswordResetToken> findToken(UUID token) {
                return Uni.createFrom().item(tokenResult);
            }
            @Override
            protected Uni<PasswordResetToken> persistToken(PasswordResetToken token) {
                return Uni.createFrom().item(token);
            }
            @Override
            protected Uni<User> persistUser(User user) {
                persistedPassword.set(user.getPassword());
                return Uni.createFrom().item(user);
            }
        };
    }

    private User userStub() {
        User u = new User();
        u.name = "alice";
        u.email = "alice@example.com";
        return u;
    }

    @Test
    void requestReset_userNichtGefunden_keinFehlerKeineEmail() {
        PasswordResetService svc = service(null, null);
        assertDoesNotThrow(() -> svc.requestReset("unknown@example.com").await().indefinitely());
        assertFalse(emailSent.get());
    }

    @Test
    void requestReset_userGefunden_sendetEmail() {
        PasswordResetService svc = service(userStub(), null);
        svc.requestReset("alice@example.com").await().indefinitely();
        assertTrue(emailSent.get());
    }

    @Test
    void resetPassword_tokenNichtGefunden_wirft404() {
        PasswordResetService svc = service(null, null);
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> svc.resetPassword(UUID.randomUUID(), "newPw123!").await().indefinitely());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void resetPassword_tokenBereitsGenutzt_wirft410() {
        PasswordResetToken token = tokenWith(true, false);
        PasswordResetService svc = service(null, token);
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> svc.resetPassword(token.token, "newPw123!").await().indefinitely());
        assertEquals(Response.Status.GONE.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void resetPassword_tokenAbgelaufen_wirft410() {
        PasswordResetToken token = tokenWith(false, true);
        PasswordResetService svc = service(null, token);
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> svc.resetPassword(token.token, "newPw123!").await().indefinitely());
        assertEquals(Response.Status.GONE.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void resetPassword_gueltigerToken_setztPasswortZurueck() {
        PasswordResetToken token = tokenWith(false, false);
        PasswordResetService svc = service(null, token);
        svc.resetPassword(token.token, "newPw123!").await().indefinitely();
        assertTrue(token.used);
        assertNotNull(persistedPassword.get());
    }

    private PasswordResetToken tokenWith(boolean used, boolean expired) {
        User user = userStub();
        PasswordResetToken t = new PasswordResetToken(user);
        t.used = used;
        if (expired) t.expiresAt = ZonedDateTime.now().minusHours(1);
        return t;
    }
}
