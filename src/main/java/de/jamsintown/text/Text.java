package de.jamsintown.text;

import de.jamsintown.story.Story;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import jakarta.persistence.*;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;

@Entity
@Table(name = "texte")
public class Text extends PanacheEntity {
    public Text(String title) {
        this.title = title;
    }
    @Column
    public String title;

    @Column
    public String description;

    public Integer priority;

    @Column
    public Integer position;

    @Column(name = "story_position")
    public Integer storyPosition = 0;

    @Column(name = "story_column")
    public Integer storyColumn = 0;

    public Text() {
    }

    public ZonedDateTime complete;

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne
    @JoinColumn(name = "group_id")
    public Gruppe group;

    @ManyToOne
    public Story story;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;

    @Column(nullable = false)
    public boolean deleted = false;

    @Column(name = "deleted_from_story_name")
    public String deletedFromStoryName;

    @Column(name = "cluster_id")
    public Long clusterId;
}
