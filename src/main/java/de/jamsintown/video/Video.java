package de.jamsintown.video;

import de.jamsintown.story.Story;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static de.jamsintown.video.VideoProcessingStatus.READY;

import java.time.ZonedDateTime;

@Entity
@Table(name = "videos")
public class Video extends PanacheEntity {

    @Column(nullable = false)
    public String pfad;

    @Column(nullable = false)
    public String title;

    @Column
    public String description;

    @Column
    public Integer priority;

    @Column(name = "story_position")
    public Integer storyPosition = 0;

    @Column(name = "story_column")
    public Integer storyColumn = 0;

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne
    @JoinColumn(name = "group_id")
    public Gruppe group;

    @ManyToOne
    public Story story;

    @Column(name = "captured_at")
    public ZonedDateTime capturedAt;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;

    @Column(nullable = false)
    public boolean deleted = false;

    @Column(name = "deleted_from_story_name")
    public String deletedFromStoryName;

    @Column(nullable = false)
    public boolean complete = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    public VideoProcessingStatus processingStatus = READY;
}