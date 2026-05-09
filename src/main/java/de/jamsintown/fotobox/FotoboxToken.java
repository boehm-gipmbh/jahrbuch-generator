package de.jamsintown.fotobox;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "fotobox_token")
public class FotoboxToken extends PanacheEntity {

    @Column(name = "group_id", nullable = false, unique = true)
    public long groupId;

    @Column(nullable = false)
    public UUID jti;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt;

    @Column(nullable = false)
    public boolean revoked;

    public static Uni<FotoboxToken> findByGroupId(long groupId) {
        return find("groupId", groupId).firstResult();
    }
}
