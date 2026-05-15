package de.jamsintown.announcement;

import de.jamsintown.user.User;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/announcements")
@RolesAllowed("admin")
public class AnnouncementResource {

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
                .map(users -> Response.ok(users.stream()
                        .map(u -> new RecipientDTO(u.id, u.name, u.email))
                        .toList()).build());
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
                .chain(users -> mailService.sendToAll(request.subject, request.body, users))
                .map(result -> Response.ok(result).build());
    }

    private Uni<List<User>> resolveRecipients(AnnouncementRequest request) {
        return switch (request.recipientFilter) {
            case GROUP -> {
                if (request.groupId == null) yield User.<User>listAll();
                yield User.<User>find("SELECT u FROM User u JOIN u.groups g WHERE g.id = ?1 AND u.active = true", request.groupId).list();
            }
            case SPECIFIC -> {
                if (request.userIds == null || request.userIds.isEmpty()) yield Uni.createFrom().item(List.of());
                yield User.<User>find("id IN ?1 AND active = true", request.userIds).list();
            }
            default -> User.<User>find("active = true").list();
        };
    }

    public record RecipientDTO(Long id, String name, String email) {}
}
