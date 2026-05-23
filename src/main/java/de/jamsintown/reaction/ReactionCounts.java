package de.jamsintown.reaction;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

public record ReactionCounts(
    long likeCount,
    long favoritCount,
    Set<Reaction.ReactionType> myReactions,
    List<ReactionInfo> likes,
    List<ReactionInfo> favoriten
) {
    public record ReactionInfo(String userName, ZonedDateTime createdAt) {}
}
