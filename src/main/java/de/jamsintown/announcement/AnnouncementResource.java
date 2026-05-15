package de.jamsintown.announcement;

import de.jamsintown.user.User;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.regex.Pattern;

@Path("/api/v1/announcements")
@RolesAllowed("admin")
public class AnnouncementResource {

    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AnnouncementMailService mailService;

    @Inject
    public AnnouncementResource(AnnouncementMailService mailService) {
        this.mailService = mailService;
    }

    @POST
    @Path("/preview-recipients")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> previewRecipients(AnnouncementRequest request) {
        return resolveRecipients(request)
                .map(recipients -> Response.ok(recipients).build());
    }

    @POST
    @Path("/send")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> send(AnnouncementRequest request) {
        if (request.subject == null || request.subject.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Betreff fehlt\"}").build());
        }
        if (request.body == null || request.body.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Text fehlt\"}").build());
        }

        return resolveRecipients(request)
                .chain(recipients -> mailService.sendToAll(request.subject, request.body, recipients))
                .map(result -> Response.ok(result).build());
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
