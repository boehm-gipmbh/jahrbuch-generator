package de.jamsintown.comment;

import de.jamsintown.reaction.Reaction;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "comments",
    indexes = {
        @Index(columnList = "target_type, target_id"),
        @Index(columnList = "parent_id")
    })
public class Comment extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    public Reaction.TargetType targetType;

    @Column(name = "target_id", nullable = false)
    public Long targetId;

    @Column(name = "parent_id")
    public Long parentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String content;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    public ZonedDateTime createdAt;
}
