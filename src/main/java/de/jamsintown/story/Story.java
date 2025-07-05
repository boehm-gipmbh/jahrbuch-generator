package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.text.Text;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "stories",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"title", "user_id"})
        }
)
public class Story extends PanacheEntity {

    @Column(nullable = false)
    public String title;

    @ManyToOne(optional = false)
    public User user;

//    @OneToMany(mappedBy = "story", fetch = FetchType.EAGER)
//   // @JoinColumn(name = "story_id")
//    public java.util.List<Bild> bilder;

//    @OneToMany(mappedBy = "story", fetch = FetchType.EAGER)
//   // @JoinColumn(name = "story_id")
//    public java.util.List<Text> texte;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;
}
