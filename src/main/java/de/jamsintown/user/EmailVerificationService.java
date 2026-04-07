package de.jamsintown.user;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

@ApplicationScoped
public class EmailVerificationService {

  private static final Logger LOG = Logger.getLogger(EmailVerificationService.class);
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private final String appUrl;
  private final String resendApiKey;
  private final boolean mock;

  @Inject
  public EmailVerificationService(
    @ConfigProperty(name = "jahrbuch.app.url") String appUrl,
    @ConfigProperty(name = "resend.api.key") String resendApiKey,
    @ConfigProperty(name = "resend.mock") boolean mock) {
    this.appUrl = appUrl;
    this.resendApiKey = resendApiKey;
    this.mock = mock;
  }

  public Uni<Void> sendVerificationMail(User user) {
    String link = appUrl + "/verify-email?token=" + user.emailVerificationToken;
    String html = """
        <p>Hallo %s,</p>
        <p>bitte bestätige deine E-Mail-Adresse:</p>
        <p><a href="%s">E-Mail bestätigen</a></p>
        <p>Der Link ist 24 Stunden gültig.</p>
        """.formatted(user.name, link);

    String json = """
        {"from":"noreply@jamsintown.de","to":["%s"],"subject":"Bitte bestätige deine E-Mail-Adresse","html":"%s"}
        """.formatted(user.email, html.replace("\"", "\\\"").replace("\n", "").strip());

    return Uni.createFrom().voidItem()
        .invoke(() -> {
          if (mock) {
            LOG.infof("Mock-Mail an %s: %s", user.email, link);
            return;
          }
          try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
              LOG.infof("Verifikationsmail an %s gesendet", user.email);
            } else {
              LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
            }
          } catch (Exception e) {
            LOG.errorf("Fehler beim Senden der Verifikationsmail: %s", e.getMessage());
          }
        });
  }

  @WithTransaction
  public Uni<Void> verify(UUID token) {
    return User.<User>find("emailVerificationToken", token).firstResult()
      .chain(user -> {
        if (user == null) {
          throw new ClientErrorException("Ungültiger oder abgelaufener Verifikationslink",
            Response.Status.GONE);
        }
        user.emailVerified = true;
        user.emailVerificationToken = null;
        return user.persistAndFlush();
      })
      .replaceWithVoid();
  }
}
