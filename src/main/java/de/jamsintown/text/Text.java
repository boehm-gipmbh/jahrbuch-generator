package de.jamsintown.text;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;

@Entity
@Table(name = "texte")
public class Text extends PanacheEntity {
    @Column(nullable = false)
    public String text;
}
