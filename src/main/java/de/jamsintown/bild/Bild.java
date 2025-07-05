package de.jamsintown.bild;

import de.jamsintown.story.Story;
import de.jamsintown.text.Text;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "bilder")
public class Bild extends PanacheEntity {
    @Column(nullable = false)
    public String pfad;

    @Column(length = 1000)
    public String description;

    public Integer quality;

    @ManyToOne(optional = false)
    public User user;

    public ZonedDateTime protect;

    @ManyToOne
   // @JoinColumn(name = "story_id")
    public Story story;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;

}
