package de.jamsintown.auth;

import de.jamsintown.user.UserService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@ApplicationScoped
public class PasswordResetService {

  private final UserService userService;
  private final PasswordResetEmailService emailService;

  @Inject
  public PasswordResetService(UserService userService, PasswordResetEmailService emailService) {
    this.userService = userService;
    this.emailService = emailService;
  }

  /**
   * Startet den Passwort-Reset-Flow für eine E-Mail-Adresse.
   * Gibt immer Void zurück, auch wenn die E-Mail nicht existiert (verhindert Email-Enumeration).
   */
  @WithTransaction
  public Uni<Void> requestReset(String email) {
    return userService.findByEmail(email)
        .chain(user -> {
          if (user == null) {
            // Kein Fehler zurückgeben — verhindert Email-Enumeration
            return Uni.createFrom().voidItem();
          }
          PasswordResetToken resetToken = new PasswordResetToken(user);
          return resetToken.<PasswordResetToken>persistAndFlush()
              .invoke(saved -> emailService.sendResetMail(user.email, user.name, saved.token.toString()))
              .replaceWithVoid();
        });
  }

  /**
   * Setzt das Passwort mit einem gültigen Reset-Token zurück.
   * Wirft 404 wenn Token nicht gefunden, 410 wenn Token abgelaufen/genutzt.
   */
  @WithTransaction
  public Uni<Void> resetPassword(UUID token, String newPassword) {
    return PasswordResetToken.<PasswordResetToken>findById(token)
        .chain(resetToken -> {
          if (resetToken == null) {
            return Uni.createFrom().failure(
                new ClientErrorException(Response.Status.NOT_FOUND));
          }
          if (!resetToken.isValid()) {
            return Uni.createFrom().failure(
                new ClientErrorException("Token abgelaufen oder bereits genutzt", Response.Status.GONE));
          }
          resetToken.used = true;
          resetToken.user.setPassword(BcryptUtil.bcryptHash(newPassword));
          return resetToken.persistAndFlush()
              .chain(() -> resetToken.user.persistAndFlush())
              .replaceWithVoid();
        });
  }
}