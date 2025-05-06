package de.jamsintown.bild;

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

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne(optional = true)
    public Text text;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;

}
