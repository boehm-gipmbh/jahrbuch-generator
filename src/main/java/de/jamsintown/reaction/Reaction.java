package de.jamsintown.reaction;

import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "reactions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "target_type", "target_id", "reaction_type"}))
public class Reaction extends PanacheEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    public TargetType targetType;

    @Column(name = "target_id", nullable = false)
    public Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 10)
    public ReactionType reactionType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    public ZonedDateTime createdAt;

    public enum TargetType { BILD, TEXT, VIDEO, COMMENT }
    public enum ReactionType { LIKE, VOTE }
}
