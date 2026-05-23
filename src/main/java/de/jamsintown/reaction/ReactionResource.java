package de.jamsintown.reaction;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/reactions")
@RolesAllowed("user")
public class ReactionResource {

    private final ReactionService reactionService;

    @Inject
    public ReactionResource(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @POST
    @Path("/{targetType}/{targetId}/{reactionType}")
    public Uni<ReactionCounts> toggle(
            @PathParam("targetType") String targetType,
            @PathParam("targetId") Long targetId,
            @PathParam("reactionType") String reactionType) {
        return reactionService.toggle(
                Reaction.TargetType.valueOf(targetType.toUpperCase()),
                targetId,
                Reaction.ReactionType.valueOf(reactionType.toUpperCase()));
    }

    @GET
    @Path("/{targetType}/{targetId}")
    public Uni<ReactionCounts> getCounts(
            @PathParam("targetType") String targetType,
            @PathParam("targetId") Long targetId) {
        return reactionService.getCounts(
                Reaction.TargetType.valueOf(targetType.toUpperCase()),
                targetId);
    }
}
