package de.jamsintown.bild;

import de.jamsintown.story.Story;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

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

    @ManyToOne(optional = false)
    public User user;

    public ZonedDateTime complete;

    @ManyToOne
   // @JoinColumn(name = "story_id")
    public Story story;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;

    @Column(name = "last_rotated", nullable = false)
    public long lastRotated = 0;
}
