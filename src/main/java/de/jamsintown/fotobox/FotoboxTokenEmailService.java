package de.jamsintown.fotobox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class FotoboxTokenEmailService {

    private static final Logger LOG = Logger.getLogger(FotoboxTokenEmailService.class);
    private final HttpClient http = HttpClient.newHttpClient();

    private final String resendApiKey;
    private final boolean mock;

    @Inject
    public FotoboxTokenEmailService(
            @ConfigProperty(name = "resend.api.key") String resendApiKey,
            @ConfigProperty(name = "resend.mock") boolean mock) {
        this.resendApiKey = resendApiKey;
        this.mock = mock;
    }

    public void sendTokenMail(String recipientEmail, String groupName, String token, String validTo) {
        String html = """
                <p>Hallo,</p>
                <p>hier ist dein Fotobox-Token für die Gruppe <strong>%s</strong> (gültig bis %s).</p>
                <p>Speichere den Token in einer Datei <code>.station-token</code> im Projektverzeichnis:</p>
                <pre style="background:#f4f4f4;padding:12px;border-radius:4px;word-break:break-all;">%s</pre>
                <p>Danach die Capture Station starten:</p>
                <pre style="background:#f4f4f4;padding:12px;border-radius:4px;">./start-capture-station.sh</pre>
                <p>Bei Fragen wende dich an deinen Administrator.</p>
                """.formatted(groupName, validTo, token);

        String subject = "Fotobox-Token — " + groupName;
        String json = """
                {"from":"noreply@jamsintown.de","to":["%s"],"subject":"%s","html":"%s"}
                """.formatted(
                recipientEmail,
                subject,
                html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").strip());

        if (mock) {
            LOG.infof("Mock-Fotobox-Token-Mail an %s (Gruppe: %s)", recipientEmail, groupName);
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
                LOG.infof("Fotobox-Token-Mail an %s gesendet", recipientEmail);
            } else {
                LOG.errorf("Resend Fehler %d: %s", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.errorf("Fehler beim Senden der Fotobox-Token-Mail: %s", e.getMessage());
        }
    }
}
