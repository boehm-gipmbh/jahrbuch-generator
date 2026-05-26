package de.jamsintown.reaction;

import de.jamsintown.notification.NotificationService;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ReactionService {

    private final UserService userService;
    private final NotificationService notificationService;

    @Inject
    public ReactionService(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @WithTransaction
    public Uni<ReactionCounts> toggle(Reaction.TargetType targetType, Long targetId, Reaction.ReactionType reactionType, String message) {
        return userService.getCurrentUser().chain(user ->
            Reaction.<Reaction>find(
                "user.id = ?1 AND targetType = ?2 AND targetId = ?3 AND reactionType = ?4",
                user.id, targetType, targetId, reactionType
            ).firstResult().chain(existing -> {
                Uni<Void> action;
                boolean isNewReport = existing == null && reactionType == Reaction.ReactionType.REPORT;
                if (existing != null) {
                    action = existing.delete();
                } else {
                    Reaction r = new Reaction();
                    r.user = user;
                    r.targetType = targetType;
                    r.targetId = targetId;
                    r.reactionType = reactionType;
                    action = r.<Reaction>persistAndFlush().replaceWithVoid();
                }
                Uni<Void> notify = isNewReport
                    ? notificationService.createReportNotifications(user, targetType, targetId, message)
                    : Uni.createFrom().voidItem();
                return action.chain(() -> notify).chain(() -> counts(targetType, targetId, user.id));
            })
        );
    }

    public Uni<ReactionCounts> getCounts(Reaction.TargetType targetType, Long targetId) {
        return userService.getCurrentUser()
            .chain(user -> counts(targetType, targetId, user.id));
    }

    private Uni<ReactionCounts> counts(Reaction.TargetType targetType, Long targetId, Long userId) {
        return Reaction.<Reaction>list(
                "targetType = ?1 AND targetId = ?2 ORDER BY createdAt ASC", targetType, targetId)
            .chain(all -> Reaction.<Reaction>list(
                    "user.id = ?1 AND targetType = ?2 AND targetId = ?3", userId, targetType, targetId)
            .map(myList -> {
                Set<Reaction.ReactionType> mine = EnumSet.noneOf(Reaction.ReactionType.class);
                myList.forEach(r -> mine.add(r.reactionType));

                List<ReactionCounts.ReactionInfo> likes = all.stream()
                    .filter(r -> r.reactionType == Reaction.ReactionType.LIKE)
                    .map(r -> new ReactionCounts.ReactionInfo(r.user.name, r.createdAt))
                    .toList();
                List<ReactionCounts.ReactionInfo> votes = all.stream()
                    .filter(r -> r.reactionType == Reaction.ReactionType.VOTE)
                    .map(r -> new ReactionCounts.ReactionInfo(r.user.name, r.createdAt))
                    .toList();
                long dislikes = all.stream()
                    .filter(r -> r.reactionType == Reaction.ReactionType.DISLIKE)
                    .count();
                long reports = all.stream()
                    .filter(r -> r.reactionType == Reaction.ReactionType.REPORT)
                    .count();

                return new ReactionCounts(likes.size(), votes.size(), dislikes, reports, mine, likes, votes);
            }));
    }

    public Uni<List<Reaction>> listForTarget(Reaction.TargetType targetType, Long targetId) {
        return Reaction.list("targetType = ?1 AND targetId = ?2", targetType, targetId);
    }
}
