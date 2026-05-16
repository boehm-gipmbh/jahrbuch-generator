package de.jamsintown.announcement;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "announcement_logs")
public class AnnouncementLog extends PanacheEntity {

    @Column(nullable = false)
    public String subject;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    public ZonedDateTime sentAt;

    @Column(name = "recipient_description", nullable = false)
    public String recipientDescription;

    @Column(name = "sent_count", nullable = false)
    public int sentCount;

    @Column(name = "failed_count", nullable = false)
    public int failedCount;

    @Column(name = "attachment_filename")
    public String attachmentFilename;
}
