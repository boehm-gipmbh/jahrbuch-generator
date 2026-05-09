package de.jamsintown.fotobox;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class FotoboxTokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    public Uni<String> generateToken(long groupId, LocalDate validFrom, LocalDate validTo) {
        UUID jti = UUID.randomUUID();
        String token = Jwt.issuer(issuer)
                .upn("fotobox-group-" + groupId)
                .groups(Set.of("fotobox"))
                .claim("group_id", groupId)
                .claim("type", "fotobox")
                .claim("jti", jti.toString())
                .issuedAt(validFrom.atStartOfDay().toInstant(ZoneOffset.UTC))
                .expiresAt(validTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
                .sign();

        return findByGroupId(groupId)
                .chain(existing -> {
                    FotoboxToken record = existing != null ? existing : new FotoboxToken();
                    record.groupId = groupId;
                    record.jti = jti;
                    record.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
                    record.revoked = false;
                    return persist(record);
                })
                .map(saved -> token);
    }

    public Uni<Void> revokeToken(long groupId) {
        return findByGroupId(groupId)
                .chain(record -> {
                    if (record == null) return Uni.createFrom().voidItem();
                    record.revoked = true;
                    return persist(record).replaceWithVoid();
                });
    }

    public Uni<Boolean> isValid(long groupId, String jti) {
        return findByGroupId(groupId)
                .map(record -> record != null && !record.revoked && record.jti.toString().equals(jti));
    }

    protected Uni<FotoboxToken> findByGroupId(long groupId) {
        return FotoboxToken.findByGroupId(groupId);
    }

    protected Uni<FotoboxToken> persist(FotoboxToken token) {
        return token.persistAndFlush();
    }
}
