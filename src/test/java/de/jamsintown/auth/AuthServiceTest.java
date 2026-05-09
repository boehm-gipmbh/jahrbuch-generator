package de.jamsintown.auth;

import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.quarkus.security.AuthenticationFailedException;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private UserService userServiceReturning(User user) {
        return new UserService(null, null) {
            @Override
            public Uni<User> findByEmail(String email) {
                return Uni.createFrom().item(user);
            }
            @Override
            public Uni<User> findByName(String name) {
                return Uni.createFrom().item(user);
            }
        };
    }

    private AuthService serviceWith(User user) {
        return new AuthService("test-issuer", userServiceReturning(user)) {
            @Override
            public String generateToken(User u) {
                return "mock-token";
            }
        };
    }

    private User validUser(String name, String password) {
        User u = new User();
        u.name = name;
        u.setPassword(io.quarkus.elytron.security.common.BcryptUtil.bcryptHash(password));
        u.active = true;
        u.emailVerified = true;
        u.roles = List.of("user");
        return u;
    }

    @Test
    void authenticate_userNichtGefunden_wirftAuthFailed() {
        AuthService svc = serviceWith(null);
        assertThrows(AuthenticationFailedException.class,
                () -> svc.authenticate(new AuthRequest("unknown", "pw")).await().indefinitely());
    }

    @Test
    void authenticate_falschesPasswort_wirftAuthFailed() {
        AuthService svc = serviceWith(validUser("alice", "correct"));
        assertThrows(AuthenticationFailedException.class,
                () -> svc.authenticate(new AuthRequest("alice", "wrong")).await().indefinitely());
    }

    @Test
    void authenticate_inactiverUser_wirftAuthFailed() {
        User u = validUser("alice", "correct");
        u.active = false;
        AuthService svc = serviceWith(u);
        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> svc.authenticate(new AuthRequest("alice", "correct")).await().indefinitely());
        assertTrue(ex.getMessage().contains("deactivated"));
    }

    @Test
    void authenticate_emailNichtVerifiziert_wirftAuthFailed() {
        User u = validUser("alice", "correct");
        u.emailVerified = false;
        AuthService svc = serviceWith(u);
        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> svc.authenticate(new AuthRequest("alice", "correct")).await().indefinitely());
        assertTrue(ex.getMessage().contains("not verified"));
    }

    @Test
    void authenticate_gueltigerUser_gibtTokenZurueck() {
        AuthService svc = serviceWith(validUser("alice", "correct"));
        String token = svc.authenticate(new AuthRequest("alice", "correct")).await().indefinitely();
        assertEquals("mock-token", token);
    }

    @Test
    void authenticate_emailLogin_nutzeFindByEmail() {
        AuthService svc = serviceWith(validUser("alice", "correct"));
        String token = svc.authenticate(new AuthRequest("alice@example.com", "correct")).await().indefinitely();
        assertEquals("mock-token", token);
    }
}
