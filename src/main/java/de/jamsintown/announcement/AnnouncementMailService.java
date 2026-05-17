package de.jamsintown.announcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class AnnouncementMailService {

    private static final Logger LOG = Logger.getLogger(AnnouncementMailService.class);
    private final HttpClient http = HttpClient.newHttpClient();

    private final String resendApiKey;
    private final boolean mock;
    private final String from;
    private final ObjectMapper mapper;

    @Inject
    public AnnouncementMailService(
            @ConfigProperty(name = "resend.api.key") String resendApiKey,
            @ConfigProperty(name = "resend.mock") boolean mock,
            @ConfigProperty(name = "resend.from", defaultValue = "noreply@jamsintown.de") String from,
            ObjectMapper mapper) {
        this.resendApiKey = resendApiKey;
        this.mock = mock;
        this.from = from;
        this.mapper = mapper;
    }

    public Uni<AnnouncementResult> sendToAll(String subject, String body, List<Recipient> recipients,
                                              String attachmentFilename, String attachmentContent) {
        if (recipients.isEmpty()) {
            return Uni.createFrom().item(new AnnouncementResult(0, 0, List.of()));
        }

        List<Recipient> filtered = recipients.stream()
                .filter(r -> r.email() != null && !r.email().isBlank())
                .toList();

        // Sequenziell senden um Resend-Rate-Limit (2 req/s) nicht zu überschreiten
        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        Uni<Void> chain = Uni.createFrom().voidItem();
        for (Recipient r : filtered) {
            chain = chain.chain(() ->
                sendOne(r.email(), r.name(), subject, body, attachmentFilename, attachmentContent)
                    .invoke(ok -> { if (ok) sent.incrementAndGet(); else failed.incrementAndGet(); })
                    .replaceWithVoid()
            );
        }
        return chain.map(ignored -> new AnnouncementResult(sent.get(), failed.get(), List.of()))
                .onFailure().recoverWithItem(e -> new AnnouncementResult(sent.get(), filtered.size() - sent.get(), List.of(e.getMessage())));
    }

    private Uni<Boolean> sendOne(String email, String name, String subject, String bodyHtml,
                                  String attachmentFilename, String attachmentContent) {
        String html = "<p>Hallo %s,</p>%s".formatted(name != null ? name : "", bodyHtml);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("from", from);
        payload.putArray("to").add(email);
        payload.put("subject", subject);
        payload.put("html", html);

        if (attachmentFilename != null && attachmentContent != null) {
            ObjectNode att = mapper.createObjectNode();
            att.put("filename", attachmentFilename);
            att.put("content", attachmentContent);
            payload.putArray("attachments").add(att);
        }

        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            LOG.errorf("JSON-Serialisierung fehlgeschlagen: %s", e.getMessage());
            return Uni.createFrom().item(false);
        }

        if (mock) {
            LOG.infof("Mock-Ankündigung an %s: %s (Anhang: %s)", email, subject,
                    attachmentFilename != null ? attachmentFilename : "keiner");
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
