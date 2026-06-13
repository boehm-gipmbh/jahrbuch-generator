package de.jamsintown.bild;

import de.jamsintown.capture.CaptureService;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.user.Gruppe;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/bilder")
@RolesAllowed("user")
public class BildResource {

    private final BildService bildService;
    private final CaptureService captureService;

    @Inject
    public BildResource(BildService bildService, CaptureService captureService) {
        this.bildService = bildService;
        this.captureService = captureService;
    }

    @GET
    public Uni<List<Bild>> get() {
        return bildService.listForUser();
    }

    @GET
    @Path("/by-group/{groupId}")
    @RolesAllowed("group-admin")
    @WithSession
    public Uni<List<Bild>> getByGroup(@PathParam("groupId") long groupId) {
        return Gruppe.<Gruppe>findById(groupId)
            .chain(g -> Bild.<Bild>find("group = ?1 and deleted = false", g).list());
    }

    @GET
    @Path("/{id}")
    public Uni<Bild> getSingle(Long id) {
        return bildService.findById(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Bild> create(Bild bild) {
        return bildService.create(bild);
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Bild> update(@PathParam("id") Long id, Bild text) {
        text.id = id;
        return bildService.update(text);
    }

    @GET
    @Path("/papierkorb")
    public Uni<List<Bild>> getDeleted() {
        return bildService.listDeleted();
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return bildService.softDelete(id);
    }

    @PUT
    @Path("/{id}/restore")
    public Uni<Bild> restore(@PathParam("id") long id) {
        return bildService.restore(id);
    }

    @DELETE
    @Path("/{id}/hard")
    public Uni<Void> hardDelete(@PathParam("id") long id) {
        return bildService.hardDelete(id);
    }

    @PUT
    @Path("/{id}/complete")
    public Uni<Boolean> setComplete(@PathParam("id") long id, boolean complete) {
        return bildService.setComplete(id, complete);
    }

    @PUT
    @Path("/{id}/hauptbild")
    public Uni<Boolean> setHauptbild(@PathParam("id") long id, boolean hauptbild) {
        return bildService.setHauptbild(id, hauptbild);
    }

    @DELETE
    @Path("/{id}/caption")
    @RolesAllowed("admin")
    public Uni<Void> clearCaption(@PathParam("id") long id) {
        return bildService.clearCaptionAdmin(id);
        }

    @DELETE
    @Path("/captions")
    @RolesAllowed("admin")
    public Uni<Integer> clearAllCaptions() {
        return bildService.clearAllCaptions();
    }

    @PUT
    @Path("/reorder/{storyId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<List<Bild>> reorder(@PathParam("storyId") Long storyId, List<Long> bildIds) {
        return bildService.reorder(storyId, bildIds);
    }
    @POST
    @Path("/capture")
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Bild> create(ImageSettings imageSettings) {
        return captureService.create(imageSettings);
    }

}
