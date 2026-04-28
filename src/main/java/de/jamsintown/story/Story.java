package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.text.Text;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "stories",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"name", "user_id"})
        }
)
public class Story extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @Column
    public String description;

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne
    @JoinColumn(name = "group_id")
    public Gruppe group;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Column(name = "layout", length = 10)
    public String layout = "2col";

    @Column(name = "order_position")
    public Integer orderPosition = 0;

    @Version
    public int version;
}
