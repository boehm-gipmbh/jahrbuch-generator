package de.jamsintown.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class PasswordResetEmailService {

  private static final Logger LOG = Logger.getLogger(PasswordResetEmailService.class);
  private final HttpClient http = HttpClient.newHttpClient();

  private final String appUrl;
  private final String resendApiKey;
  private final boolean mock;

  @Inject
  public PasswordResetEmailService(
    @ConfigProperty(name = "jahrbuch.app.url") String appUrl,
    @ConfigProperty(name = "resend.api.key") String resendApiKey,
    @ConfigProperty(name = "resend.mock") boolean mock) {
    this.appUrl = appUrl;
    this.resendApiKey = resendApiKey;
    this.mock = mock;
  }

  public void sendResetMail(String email, String userName, String token) {
    String link = appUrl + "/reset-password?token=" + token;
    String html = """
        <p>Hallo %s,</p>
        <p>du hast eine Anfrage zum Zurücksetzen deines Passworts gestellt.</p>
        <p><a href="%s">Passwort zurücksetzen</a></p>
        <p>Der Link ist 24 Stunden gültig. Falls du diese Anfrage nicht gestellt hast, kannst du diese E-Mail ignorieren.</p>
        """.formatted(userName, link);

    String json = """
        {"from":"noreply@jamsintown.de","to":["%s"],"subject":"Passwort zurücksetzen","html":"%s"}
        """.formatted(email, html.replace("\"", "\\\"").replace("\n", "").strip());

    if (mock) {
      LOG.infof("Mock-Reset-Mail an %s: %s", email, link);
      return;
    }

    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.resend.com/emails"))
          .header("Authorization", "Bearer " + resendApiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.infof("Passwort-Reset-Mail an %s gesendet", email);
      } else {
        LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
      }
    } catch (Exception e) {
      LOG.errorf("Fehler beim Senden der Reset-Mail: %s", e.getMessage());
    }
  }
}