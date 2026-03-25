package de.jamsintown.text;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/texte")
@RolesAllowed("user")
public class TextResource {
    private final TextService textService;

    public TextResource(TextService textService) {
        this.textService = textService;
    }

    @GET
    public Uni<List<Text>> get() {
        return textService.listForUser();
    }

    @GET
    @Path("/{id}")
    public Uni<Text> getSingle(Long id) {
        return textService.findByIdForUser(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Text> create(Text text) {
        return textService.create(text);
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Text> update(@PathParam("id") Long id, Text text) {
        text.id = id;
        return textService.update(text);
    }

    @GET
    @Path("/papierkorb")
    public Uni<List<Text>> getDeleted() {
        return textService.listDeleted();
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return textService.softDelete(id);
    }

    @PUT
    @Path("/{id}/restore")
    public Uni<Text> restore(@PathParam("id") long id) {
        return textService.restore(id);
    }

    @DELETE
    @Path("/{id}/hard")
    public Uni<Void> hardDelete(@PathParam("id") long id) {
        return textService.hardDelete(id);
    }

    @PUT
    @Path("/{id}/complete")
    public Uni<Boolean> setComplete(@PathParam("id") long id, boolean complete) {
        return textService.setComplete(id, complete);
    }

    @PUT
    @Path("/reorder/{storyId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<List<Text>> reorder(@PathParam("storyId") Long storyId, List<Long> textIds) {
        return textService.reorder(storyId, textIds);
    }

}
