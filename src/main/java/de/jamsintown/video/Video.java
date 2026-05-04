package de.jamsintown.video;

import de.jamsintown.story.Story;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(nullable = false)
    public boolean complete = false;
}