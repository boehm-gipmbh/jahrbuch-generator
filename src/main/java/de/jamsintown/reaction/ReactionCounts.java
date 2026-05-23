package de.jamsintown.reaction;

import java.util.Set;

public record ReactionCounts(
    long likeCount,
    long favoritCount,
    Set<Reaction.ReactionType> myReactions
) {}
