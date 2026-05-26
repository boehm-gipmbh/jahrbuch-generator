package de.jamsintown.comment;

import de.jamsintown.reaction.Reaction;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/comments")
@RolesAllowed("user")
public class CommentResource {

    private final CommentService commentService;

    @Inject
    public CommentResource(CommentService commentService) {
        this.commentService = commentService;
    }

    @GET
    @Path("/{targetType}/{targetId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<CommentDTO>> getComments(
            @PathParam("targetType") String targetType,
            @PathParam("targetId") Long targetId) {
        return commentService.getComments(
                Reaction.TargetType.valueOf(targetType.toUpperCase()),
                targetId);
    }

    @POST
    @Path("/{targetType}/{targetId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<CommentDTO> addComment(
            @PathParam("targetType") String targetType,
            @PathParam("targetId") Long targetId,
            CommentRequest req) {
        return commentService.addComment(
                Reaction.TargetType.valueOf(targetType.toUpperCase()),
                targetId,
                req);
    }

    @DELETE
    @Path("/{commentId}")
    public Uni<Response> deleteComment(@PathParam("commentId") Long commentId) {
        return commentService.deleteComment(commentId)
                .map(v -> Response.noContent().build());
    }
}
