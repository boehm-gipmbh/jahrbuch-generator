package de.jamsintown.bild;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.jamsintown.story.Story;
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

    @Column(nullable = false)
    public String title;

    @Column(length = 1000)
    public String description;

    public Integer priority;

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

    public void setPfad(String pfad) {
        this.pfad = pfad;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;

    }
}
