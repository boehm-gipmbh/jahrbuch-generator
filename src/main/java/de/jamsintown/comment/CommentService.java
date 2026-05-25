package de.jamsintown.comment;

import de.jamsintown.reaction.Reaction;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class CommentService {

    private final UserService userService;

    @Inject
    public CommentService(UserService userService) {
        this.userService = userService;
    }

    public Uni<List<CommentDTO>> getComments(Reaction.TargetType targetType, Long targetId) {
        return userService.getCurrentUser().chain(user ->
            Comment.<Comment>list("targetType = ?1 AND targetId = ?2 ORDER BY createdAt ASC", targetType, targetId)
                .map(all -> toTree(all, user.id))
        );
    }

    @WithTransaction
    public Uni<CommentDTO> addComment(Reaction.TargetType targetType, Long targetId, CommentRequest req) {
        return userService.getCurrentUser().chain(user -> {
            Comment c = new Comment();
            c.targetType = targetType;
            c.targetId = targetId;
            c.parentId = req.parentId();
            c.content = req.content().trim();
            c.user = user;
            return c.<Comment>persistAndFlush()
                .map(saved -> CommentDTO.from(saved, user.id, List.of()));
        });
    }

    @WithTransaction
    public Uni<Void> deleteComment(Long commentId) {
        return userService.getCurrentUser().chain(user ->
            Comment.<Comment>findById(commentId).chain(c -> {
                if (c == null) throw new NotFoundException();
                if (!c.user.id.equals(user.id)) throw new ForbiddenException();
                return c.delete();
            })
        );
    }

    private List<CommentDTO> toTree(List<Comment> all, Long myUserId) {
        Map<Long, List<Comment>> byParent = all.stream()
            .filter(c -> c.parentId != null)
            .collect(Collectors.groupingBy(c -> c.parentId));

        List<CommentDTO> roots = new ArrayList<>();
        for (Comment c : all) {
            if (c.parentId == null) {
                List<CommentDTO> replies = byParent.getOrDefault(c.id, List.of()).stream()
                    .map(r -> CommentDTO.from(r, myUserId, List.of()))
                    .toList();
                roots.add(CommentDTO.from(c, myUserId, replies));
            }
        }
        return roots;
    }
}
