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
public class InvitationEmailService {

  private static final Logger LOG = Logger.getLogger(InvitationEmailService.class);
  private final HttpClient http = HttpClient.newHttpClient();

  private final String appUrl;
  private final String resendApiKey;
  private final boolean mock;

  @Inject
  public InvitationEmailService(
    @ConfigProperty(name = "jahrbuch.app.url") String appUrl,
    @ConfigProperty(name = "resend.api.key") String resendApiKey,
    @ConfigProperty(name = "resend.mock") boolean mock) {
    this.appUrl = appUrl;
    this.resendApiKey = resendApiKey;
    this.mock = mock;
  }

  public void sendInvitationMail(InvitationToken token) {
    String link = appUrl + "/register?token=" + token.token;
    String groupInfo = token.label != null && !token.label.isBlank()
        ? " zur Gruppe <strong>" + token.label + "</strong>" : "";
    String html = """
        <p>Hallo,</p>
        <p>du wurdest%s eingeladen.</p>
        <p><a href="%s">Jetzt registrieren</a></p>
        <p>Der Link ist bis %s gültig.</p>
        """.formatted(groupInfo, link, token.expiresAt.toLocalDate());

    String json = """
        {"from":"noreply@jamsintown.de","to":["%s"],"subject":"Du wurdest eingeladen","html":"%s"}
        """.formatted(token.recipientEmail,
        html.replace("\"", "\\\"").replace("\n", "").strip());

    if (mock) {
      LOG.infof("Mock-Einladungsmail an %s: %s", token.recipientEmail, link);
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
        LOG.infof("Einladungsmail an %s gesendet", token.recipientEmail);
      } else {
        LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
      }
    } catch (Exception e) {
      LOG.errorf("Fehler beim Senden der Einladungsmail: %s", e.getMessage());
    }
  }
}