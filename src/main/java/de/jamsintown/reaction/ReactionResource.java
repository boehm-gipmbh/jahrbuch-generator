package de.jamsintown.reaction;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

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
            @PathParam("targetType") Reaction.TargetType targetType,
            @PathParam("targetId") Long targetId,
            @PathParam("reactionType") Reaction.ReactionType reactionType) {
        return reactionService.toggle(targetType, targetId, reactionType);
    }

    @GET
    @Path("/{targetType}/{targetId}")
    public Uni<ReactionCounts> getCounts(
            @PathParam("targetType") Reaction.TargetType targetType,
            @PathParam("targetId") Long targetId) {
        return reactionService.getCounts(targetType, targetId);
    }
}
