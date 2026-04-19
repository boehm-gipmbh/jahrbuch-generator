package de.jamsintown.user;

import io.smallrye.mutiny.Uni;
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

  public Uni<String> sendReminderMail(User user, String groupName) {
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
      return Uni.createFrom().nullItem();
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.resend.com/emails"))
        .header("Authorization", "Bearer " + resendApiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();

    return Uni.createFrom().completionStage(() -> http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
        .map(response -> {
          if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LOG.infof("Reminder an %s gesendet", user.email);
            return extractResendId(response.body());
          } else {
            LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
            return null;
          }
        })
        .onFailure().recoverWithItem(e -> {
          LOG.errorf("Fehler beim Senden der Reminder-Mail: %s", e.getMessage());
          return null;
        });
  }

  public Uni<String> getDeliveryStatus(String resendMessageId) {
    if (resendMessageId == null) return Uni.createFrom().item("unknown");

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.resend.com/emails/" + resendMessageId))
        .header("Authorization", "Bearer " + resendApiKey)
        .GET()
        .build();

    return Uni.createFrom().completionStage(() -> http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
        .map(response -> {
          if (response.statusCode() == 200) {
            String body = response.body();
            int start = body.indexOf("\"last_event\":\"");
            if (start < 0) start = body.indexOf("\"status\":\"");
            if (start < 0) return "unknown";
            start = body.indexOf("\"", start) + 1;
            start = body.indexOf("\"", start) + 1;
            int end = body.indexOf("\"", start);
            return end > start ? body.substring(start, end) : "unknown";
          }
          return "unknown";
        })
        .onFailure().recoverWithItem(e -> {
          LOG.errorf("Fehler beim Abrufen des Resend-Status: %s", e.getMessage());
          return "unknown";
        });
  }

  private String extractResendId(String body) {
    int start = body.indexOf("\"id\":\"");
    if (start < 0) return null;
    start += 6;
    int end = body.indexOf("\"", start);
    return end > start ? body.substring(start, end) : null;
  }
}
