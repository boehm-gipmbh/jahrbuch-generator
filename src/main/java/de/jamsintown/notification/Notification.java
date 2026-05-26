package de.jamsintown.notification;

import de.jamsintown.reaction.Reaction;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "notifications")
public class Notification extends PanacheEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    public User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    public Reaction.TargetType targetType;

    @Column(name = "target_id", nullable = false)
    public Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public NotificationType type;

    @Column(name = "reporter_name")
    public String reporterName;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(name = "read_at")
    public ZonedDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    public ZonedDateTime createdAt;

    public enum NotificationType { REPORT }
}
