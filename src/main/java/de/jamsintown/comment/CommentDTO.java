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
    List<CommentDTO> replies
) {
    static CommentDTO from(Comment c, Long myUserId, List<CommentDTO> replies) {
        return new CommentDTO(c.id, c.content, c.user.name, c.createdAt, c.parentId, c.user.id.equals(myUserId), replies);
    }
}
