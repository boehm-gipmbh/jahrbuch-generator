package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.text.Text;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "text_bild_link",
    uniqueConstraints = @UniqueConstraint(columnNames = {"text_id", "bild_id"}))
public class TextBildLink extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_id", nullable = false)
    public Text text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bild_id", nullable = false)
    public Bild bild;
}