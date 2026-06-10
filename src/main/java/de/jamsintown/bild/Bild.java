package de.jamsintown.bild;

import de.jamsintown.story.Story;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "bilder")
public class Bild extends PanacheEntity {
    @Column(nullable = false)
    public String pfad;

    @Column(nullable = false)
    public String title;

    @Column
    public String description;

    @Column
    public Integer priority;

    @Column
    public Integer position;

    @Column(name = "story_position")
    public Integer storyPosition = 0;

    @Column(name = "story_column")
    public Integer storyColumn = 0;

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne
    @JoinColumn(name = "group_id")
    public Gruppe group;

    public ZonedDateTime complete;

    @ManyToOne
   // @JoinColumn(name = "story_id")
    public Story story;

    @Column(name = "captured_at")
    public ZonedDateTime capturedAt;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;

    @Column(name = "last_rotated", nullable = false)
    public long lastRotated = 0;

    @Column(nullable = false)
    public boolean deleted = false;

    @Column(nullable = false)
    public boolean hauptbild = false;

    @Column(name = "deleted_from_story_name")
    public String deletedFromStoryName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exif_data", columnDefinition = "jsonb")
    public String exifData;

    @Column(name = "caption", columnDefinition = "text")
    public String caption;

    @Column(name = "cluster_id")
    public Long clusterId;
}
