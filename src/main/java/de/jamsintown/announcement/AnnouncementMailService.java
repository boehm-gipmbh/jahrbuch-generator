package de.jamsintown.announcement;

import de.jamsintown.user.User;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AnnouncementMailService {

    private static final Logger LOG = Logger.getLogger(AnnouncementMailService.class);
    private final HttpClient http = HttpClient.newHttpClient();

    private final String resendApiKey;
    private final boolean mock;

    @Inject
    public AnnouncementMailService(
            @ConfigProperty(name = "resend.api.key") String resendApiKey,
            @ConfigProperty(name = "resend.mock") boolean mock) {
        this.resendApiKey = resendApiKey;
        this.mock = mock;
    }

    public Uni<AnnouncementResult> sendToAll(String subject, String body, List<User> recipients) {
        if (recipients.isEmpty()) {
            return Uni.createFrom().item(new AnnouncementResult(0, 0, List.of()));
        }

        List<Uni<Boolean>> sends = recipients.stream()
                .filter(u -> u.email != null && !u.email.isBlank())
                .map(u -> sendOne(u.email, u.name, subject, body))
                .toList();

        return Uni.join().all(sends).andFailFast()
                .map(results -> {
                    int sent = (int) results.stream().filter(Boolean::booleanValue).count();
                    int failed = results.size() - sent;
                    return new AnnouncementResult(sent, failed, List.of());
                })
                .onFailure().recoverWithItem(e -> new AnnouncementResult(0, recipients.size(), List.of(e.getMessage())));
    }

    private Uni<Boolean> sendOne(String email, String name, String subject, String bodyHtml) {
        String html = "<p>Hallo %s,</p>%s".formatted(name != null ? name : "", bodyHtml);
        String json = """
                {"from":"noreply@jamsintown.de","to":["%s"],"subject":"%s","html":"%s"}
                """.formatted(
                email,
                subject.replace("\"", "\\\""),
                html.replace("\"", "\\\"").replace("\n", "<br>").strip());

        if (mock) {
            LOG.infof("Mock-Ankündigung an %s: %s", email, subject);
            return Uni.createFrom().item(true);
        }

        io.vertx.core.Context ctx = io.vertx.core.Vertx.currentContext();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        Uni<Boolean> result = Uni.createFrom()
                .completionStage(() -> http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .map(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        LOG.infof("Ankündigung an %s gesendet", email);
                        return true;
                    }
                    LOG.errorf("Resend Fehler %d für %s: %s", response.statusCode(), email, response.body());
                    return false;
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.errorf("Fehler beim Senden an %s: %s", email, e.getMessage());
                    return false;
                });

        return ctx != null ? result.emitOn(cmd -> ctx.runOnContext(v -> cmd.run())) : result;
    }
}
