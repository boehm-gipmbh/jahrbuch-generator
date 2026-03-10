package de.jamsintown.text;

import de.jamsintown.story.Story;
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

    public Text() {
    }

    public ZonedDateTime complete;

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne
    public Story story;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;
}
