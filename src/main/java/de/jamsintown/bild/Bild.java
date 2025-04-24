package de.jamsintown.bild;

import de.jamsintown.project.Project;
import de.jamsintown.text.Text;
import de.jamsintown.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "bilder")
public class    Bild extends PanacheEntity {
    @Column(nullable = false)
    public String pfad;

    @Column(length = 1000)
    public String description;

    @ManyToOne(optional = false)
    public User user;

    @ManyToOne
    public Project project;

    @ManyToMany
    @JoinTable(
            name = "bilder_texte",
            joinColumns = @JoinColumn(name = "bild_id"),
            inverseJoinColumns = @JoinColumn(name = "text_id")
    )
    public Set<Text> texte = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    public ZonedDateTime created;

    @Version
    public int version;

}
