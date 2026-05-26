package de.jamsintown.notification;

import de.jamsintown.reaction.Reaction;

import java.time.ZonedDateTime;

public record NotificationDTO(
    Long id,
    Reaction.TargetType targetType,
    Long targetId,
    Notification.NotificationType type,
    String reporterName,
    String message,
    ZonedDateTime createdAt
) {
    static NotificationDTO from(Notification n) {
        return new NotificationDTO(n.id, n.targetType, n.targetId, n.type, n.reporterName, n.message, n.createdAt);
    }
}
