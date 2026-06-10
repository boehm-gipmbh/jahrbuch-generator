package de.jamsintown.story;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cluster")
public class Cluster extends PanacheEntity {
}