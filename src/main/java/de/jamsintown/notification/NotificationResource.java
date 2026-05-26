package de.jamsintown.notification;

import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/notifications")
@RolesAllowed("user")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    private final NotificationService notificationService;
    private final UserService userService;

    @Inject
    public NotificationResource(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GET
    public Uni<List<NotificationDTO>> getUnread() {
        return userService.getCurrentUser()
            .chain(me -> notificationService.getUnread(me.id));
    }

    @GET
    @Path("/count")
    public Uni<Long> countUnread() {
        return userService.getCurrentUser()
            .chain(me -> notificationService.countUnread(me.id));
    }

    @PUT
    @Path("/{id}/read")
    public Uni<Void> markRead(@PathParam("id") Long id) {
        return userService.getCurrentUser()
            .chain(me -> notificationService.markRead(id, me.id));
    }

    @PUT
    @Path("/read-all")
    public Uni<Void> markAllRead() {
        return userService.getCurrentUser()
            .chain(me -> notificationService.markAllRead(me.id));
    }
}
