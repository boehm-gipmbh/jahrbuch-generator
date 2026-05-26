package de.jamsintown.comment;

import de.jamsintown.reaction.Reaction;
import de.jamsintown.reaction.Reaction.TargetType;
import de.jamsintown.reaction.Reaction.ReactionType;
import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class CommentService {

    private final UserService userService;
    private final JsonWebToken jwt;

    @Inject
    public CommentService(UserService userService, JsonWebToken jwt) {
        this.userService = userService;
        this.jwt = jwt;
    }

    public Uni<List<CommentDTO>> getComments(TargetType targetType, Long targetId) {
        return buildDeletionContext().chain(ctx ->
            Comment.<Comment>list("targetType = ?1 AND targetId = ?2 ORDER BY createdAt ASC", targetType, targetId)
                .chain(all -> {
                    if (all.isEmpty()) return Uni.createFrom().item(List.of());
                    List<Long> ids = all.stream().map(c -> c.id).toList();
                    return Reaction.<Reaction>list(
                        "targetType = ?1 AND reactionType = ?2 AND targetId IN ?3",
                        TargetType.COMMENT, ReactionType.DISLIKE, ids
                    ).map(dislikes -> {
                        Set<Long> dislikedIds = dislikes.stream()
                            .map(r -> r.targetId)
                            .collect(Collectors.toSet());
                        return toTree(all, ctx, dislikedIds);
                    });
                })
        );
    }

    @WithTransaction
    public Uni<CommentDTO> addComment(TargetType targetType, Long targetId, CommentRequest req) {
        return userService.getCurrentUser().chain(user -> {
            Comment c = new Comment();
            c.targetType = targetType;
            c.targetId = targetId;
            c.parentId = req.parentId();
            c.content = req.content().trim();
            c.user = user;
            return c.<Comment>persistAndFlush()
                .map(saved -> CommentDTO.from(saved, user.id, true, false, List.of()));
        });
    }

    @WithTransaction
    public Uni<Void> deleteComment(Long commentId) {
        return buildDeletionContext().chain(ctx ->
            Comment.<Comment>findById(commentId).chain(c -> {
                if (c == null) throw new NotFoundException();
                if (c.deletedAt != null) throw new NotFoundException();
                if (!ctx.canDelete(c.user.id)) throw new ForbiddenException();
                c.deletedAt = java.time.ZonedDateTime.now();
                return c.<Comment>persistAndFlush().replaceWithVoid();
            })
        );
    }

    // --- helpers ---

    private Uni<DeletionContext> buildDeletionContext() {
        boolean isAdmin = jwt.getGroups().contains("admin");
        boolean isGroupAdmin = jwt.getGroups().contains("group-admin") && !isAdmin;

        return userService.getCurrentUser().chain(me -> {
            if (isAdmin) {
                return Uni.createFrom().item(new DeletionContext(me.id, true, null));
            }
            if (isGroupAdmin && me.activeGroup != null) {
                boolean managesActive = me.managedGroups != null &&
                    me.managedGroups.stream().anyMatch(g -> g.id.equals(me.activeGroup.id));
                if (managesActive) {
                    return User.<User>list(
                        "SELECT u FROM User u JOIN u.groups g WHERE g.id = ?1", me.activeGroup.id
                    ).map(members -> {
                        Set<Long> ids = members.stream().map(u -> u.id).collect(Collectors.toSet());
                        return new DeletionContext(me.id, false, ids);
                    });
                }
            }
            return Uni.createFrom().item(new DeletionContext(me.id, false, Set.of(me.id)));
        });
    }

    private List<CommentDTO> toTree(List<Comment> all, DeletionContext ctx, Set<Long> dislikedIds) {
        var byParent = all.stream()
            .filter(c -> c.parentId != null)
            .collect(Collectors.groupingBy(c -> c.parentId));

        return all.stream()
            .filter(c -> c.parentId == null)
            .map(c -> {
                List<CommentDTO> replies = byParent.getOrDefault(c.id, List.of()).stream()
                    .map(r -> CommentDTO.from(r, ctx.myId(), ctx.canDelete(r.user.id), dislikedIds.contains(r.id), List.of()))
                    .toList();
                return CommentDTO.from(c, ctx.myId(), ctx.canDelete(c.user.id), dislikedIds.contains(c.id), replies);
            })
            .toList();
    }

    private record DeletionContext(Long myId, boolean isFullAdmin, Set<Long> deletableUserIds) {
        boolean canDelete(Long authorId) {
            if (isFullAdmin) return true;
            if (deletableUserIds == null) return false;
            return deletableUserIds.contains(authorId);
        }
    }
}
