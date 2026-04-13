package de.jamsintown.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class ReminderEmailService {

  private static final Logger LOG = Logger.getLogger(ReminderEmailService.class);
  private final HttpClient http = HttpClient.newHttpClient();

  private final String appUrl;
  private final String resendApiKey;
  private final boolean mock;

  @Inject
  public ReminderEmailService(
      @ConfigProperty(name = "jahrbuch.app.url") String appUrl,
      @ConfigProperty(name = "resend.api.key") String resendApiKey,
      @ConfigProperty(name = "resend.mock") boolean mock) {
    this.appUrl = appUrl;
    this.resendApiKey = resendApiKey;
    this.mock = mock;
  }

  public void sendReminderMail(User user, String groupName) {
    String groupInfo = groupName != null && !groupName.isBlank()
        ? " für <strong>" + groupName + "</strong>" : "";
    String html = """
        <p>Hallo %s,</p>
        <p>denk daran, deine Bilder und Texte%s hochzuladen!</p>
        <p>Wir freuen uns auf deinen Beitrag.</p>
        <p><a href="%s">Jetzt Bilder und Texte hochladen</a></p>
        """.formatted(user.name, groupInfo, appUrl);

    String json = """
        {"from":"noreply@jamsintown.de","to":["%s"],"subject":"Erinnerung: Bilder und Texte hochladen","html":"%s"}
        """.formatted(user.email,
        html.replace("\"", "\\\"").replace("\n", "").strip());

    if (mock) {
      LOG.infof("Mock-Reminder an %s (%s)", user.name, user.email);
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
        LOG.infof("Reminder an %s gesendet", user.email);
      } else {
        LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
      }
    } catch (Exception e) {
      LOG.errorf("Fehler beim Senden der Reminder-Mail: %s", e.getMessage());
    }
  }
}
