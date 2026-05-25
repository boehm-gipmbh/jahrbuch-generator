package de.jamsintown.comment;

import java.time.ZonedDateTime;
import java.util.List;

public record CommentDTO(
    Long id,
    String content,
    String userName,
    ZonedDateTime createdAt,
    Long parentId,
    boolean mine,
    boolean canDelete,
    boolean deleted,
    boolean excludedFromPdf,
    List<CommentDTO> replies
) {
    static CommentDTO from(Comment c, Long myUserId, boolean canDelete, boolean excludedFromPdf, List<CommentDTO> replies) {
        boolean deleted = c.deletedAt != null;
        return new CommentDTO(
            c.id,
            deleted ? null : c.content,
            deleted ? null : c.user.name,
            c.createdAt,
            c.parentId,
            !deleted && c.user.id.equals(myUserId),
            !deleted && canDelete,
            deleted,
            excludedFromPdf,
            replies
        );
    }
}
