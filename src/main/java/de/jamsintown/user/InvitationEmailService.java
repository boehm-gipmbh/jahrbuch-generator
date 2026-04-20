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

  /** Sendet die Einladungsmail asynchron und gibt die Resend-Message-ID zurück (null im Mock oder bei Fehler). */
  public Uni<String> sendInvitationMail(InvitationToken token) {
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
      return Uni.createFrom().nullItem();
    }

    io.vertx.core.Context ctx = io.vertx.core.Vertx.currentContext();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.resend.com/emails"))
        .header("Authorization", "Bearer " + resendApiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();

    Uni<String> result = Uni.createFrom().completionStage(() -> http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
        .map(response -> {
          if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LOG.infof("Einladungsmail an %s gesendet", token.recipientEmail);
            return extractResendId(response.body());
          } else {
            LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
            return null;
          }
        })
        .onFailure().recoverWithItem(e -> {
          LOG.errorf("Fehler beim Senden der Einladungsmail: %s", e.getMessage());
          return null;
        });

    return ctx != null ? result.emitOn(cmd -> ctx.runOnContext(v -> cmd.run())) : result;
  }

  public Uni<String> getDeliveryStatus(String resendMessageId) {
    if (resendMessageId == null || resendMessageId.isBlank()) return Uni.createFrom().item("unknown");

    io.vertx.core.Context ctx = io.vertx.core.Vertx.currentContext();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.resend.com/emails/" + resendMessageId))
        .header("Authorization", "Bearer " + resendApiKey)
        .GET()
        .build();

    Uni<String> result = Uni.createFrom().completionStage(() -> http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
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

    return ctx != null ? result.emitOn(cmd -> ctx.runOnContext(v -> cmd.run())) : result;
  }

  private String extractResendId(String body) {
    int start = body.indexOf("\"id\":\"");
    if (start < 0) return null;
    start += 6;
    int end = body.indexOf("\"", start);
    return end > start ? body.substring(start, end) : null;
  }
}
