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
public class UsernameReminderEmailService {

  private static final Logger LOG = Logger.getLogger(UsernameReminderEmailService.class);
  private final HttpClient http = HttpClient.newHttpClient();

  private final String appUrl;
  private final String resendApiKey;
  private final boolean mock;

  @Inject
  public UsernameReminderEmailService(
    @ConfigProperty(name = "jahrbuch.app.url") String appUrl,
    @ConfigProperty(name = "resend.api.key") String resendApiKey,
    @ConfigProperty(name = "resend.mock") boolean mock) {
    this.appUrl = appUrl;
    this.resendApiKey = resendApiKey;
    this.mock = mock;
  }

  public void sendUsernameReminder(String email, String userName) {
    String html = """
        <p>Hallo,</p>
        <p>du hast nach deinem Benutzernamen gefragt. Dein Benutzername lautet:</p>
        <p><strong>%s</strong></p>
        <p><a href="%s/login">Jetzt anmelden</a></p>
        """.formatted(userName, appUrl);

    String json = """
        {"from":"noreply@jamsintown.de","to":["%s"],"subject":"Dein Benutzername","html":"%s"}
        """.formatted(email, html.replace("\"", "\\\"").replace("\n", "").strip());

    if (mock) {
      LOG.infof("Mock-Username-Reminder an %s: Username ist '%s'", email, userName);
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
        LOG.infof("Username-Reminder an %s gesendet", email);
      } else {
        LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
      }
    } catch (Exception e) {
      LOG.errorf("Fehler beim Senden der Username-Reminder-Mail: %s", e.getMessage());
    }
  }
}