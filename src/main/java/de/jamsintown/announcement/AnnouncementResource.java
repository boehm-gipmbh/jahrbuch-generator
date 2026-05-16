package de.jamsintown.announcement;

import de.jamsintown.pdf.PdfService;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.panache.common.Sort;

import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Path("/api/v1/announcements")
@RolesAllowed("admin")
public class AnnouncementResource {

    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AnnouncementMailService mailService;
    private final PdfService pdfService;

    @Inject
    public AnnouncementResource(AnnouncementMailService mailService, PdfService pdfService) {
        this.mailService = mailService;
        this.pdfService = pdfService;
    }

    @GET
    @Path("/history")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<Response> history() {
        return AnnouncementLog.<AnnouncementLog>findAll(Sort.descending("sentAt"))
                .page(0, 20).list()
                .map(logs -> Response.ok(logs).build());
    }

    @POST
    @Path("/preview-recipients")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<Response> previewRecipients(AnnouncementRequest request) {
        return resolveRecipients(request)
                .map(recipients -> Response.ok(recipients).build());
    }

    @POST
    @Path("/send")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<Response> send(AnnouncementRequest request) {
        if (request.subject == null || request.subject.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Betreff fehlt\"}").build());
        }
        if (request.body == null || request.body.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Text fehlt\"}").build());
        }

        return resolveAttachment(request)
                .chain(() -> resolveRecipientDescription(request))
                .chain(description -> resolveRecipients(request)
                        .chain(recipients -> mailService.sendToAll(request.subject, request.body, recipients,
                                request.attachmentFilename, request.attachmentContent))
                        .chain(result -> persistLog(request, description, result).replaceWith(result)))
                .map(result -> Response.ok(result).build());
    }

    private Uni<Void> persistLog(AnnouncementRequest request, String recipientDescription, AnnouncementResult result) {
        AnnouncementLog log = new AnnouncementLog();
        log.subject = request.subject;
        log.recipientDescription = recipientDescription;
        log.sentCount = result.sent;
        log.failedCount = result.failed;
        log.attachmentFilename = request.attachmentFilename;
        return Panache.withTransaction(() -> log.<AnnouncementLog>persist().replaceWithVoid());
    }

    private Uni<String> resolveRecipientDescription(AnnouncementRequest request) {
        return switch (request.recipientFilter) {
            case GROUP -> {
                if (request.groupId == null) yield Uni.createFrom().item("Alle Nutzer");
                yield Gruppe.<Gruppe>findById(request.groupId)
                        .map(g -> g != null ? "Gruppe: " + g.name : "Gruppe #" + request.groupId);
            }
            case SPECIFIC -> Uni.createFrom().item(
                    request.userIds == null ? "0 Nutzer" : request.userIds.size() + " Nutzer");
            case EXTERNAL -> Uni.createFrom().item(
                    request.externalEmails == null ? "0 extern" : request.externalEmails.size() + " externe Adressen");
            default -> Uni.createFrom().item("Alle aktiven Nutzer");
        };
    }

    private Uni<Void> resolveAttachment(AnnouncementRequest request) {
        if (request.attachmentGroupId != null) {
            return pdfService.generateForGroup(request.attachmentGroupId)
                    .invoke(bytes -> {
                        request.attachmentFilename = "jahrbuch-" + request.attachmentGroupId + ".pdf";
                        request.attachmentContent = Base64.getEncoder().encodeToString(bytes);
                    })
                    .replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    private Uni<List<Recipient>> resolveRecipients(AnnouncementRequest request) {
        return switch (request.recipientFilter) {
            case GROUP -> {
                if (request.groupId == null) yield usersToRecipients(User.<User>listAll());
                yield usersToRecipients(User.<User>find(
                        "SELECT u FROM User u JOIN u.groups g WHERE g.id = ?1 AND u.active = true",
                        request.groupId).list());
            }
            case SPECIFIC -> {
                if (request.userIds == null || request.userIds.isEmpty())
                    yield Uni.createFrom().item(List.of());
                yield usersToRecipients(User.<User>find("id IN ?1 AND active = true", request.userIds).list());
            }
            case EXTERNAL -> {
                List<String> emails = request.externalEmails == null ? List.of() : request.externalEmails;
                List<Recipient> recipients = emails.stream()
                        .map(String::trim)
                        .filter(e -> EMAIL_RE.matcher(e).matches())
                        .map(Recipient::fromEmail)
                        .toList();
                yield Uni.createFrom().item(recipients);
            }
            default -> usersToRecipients(User.<User>find("active = true").list());
        };
    }

    private Uni<List<Recipient>> usersToRecipients(Uni<List<User>> usersUni) {
        return usersUni.map(users -> users.stream()
                .map(u -> new Recipient(u.id, u.name, u.email))
                .toList());
    }
}
