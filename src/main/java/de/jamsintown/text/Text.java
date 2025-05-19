package de.jamsintown.text;

import de.jamsintown.user.User;
import jakarta.persistence.*;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;

@Entity
@Table(name = "texte")
public class Text extends PanacheEntity {
    public Text(String text, String title) {
        this.text = text;
        this.title = title;
    }
    public Text() {
    }

    @Column(nullable = false)
    public String title;

    @Column(nullable = false)
    public String text;

    @ManyToOne(optional = false)
    public User user;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;
}
