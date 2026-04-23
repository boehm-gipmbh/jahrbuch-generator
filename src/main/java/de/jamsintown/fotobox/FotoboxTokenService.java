package de.jamsintown.fotobox;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

@ApplicationScoped
public class FotoboxTokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    public String generateToken(long groupId, LocalDate validFrom, LocalDate validTo) {
        return Jwt.issuer(issuer)
                .upn("fotobox-group-" + groupId)
                .groups(Set.of("fotobox"))
                .claim("group_id", groupId)
                .claim("type", "fotobox")
                .issuedAt(validFrom.atStartOfDay().toInstant(ZoneOffset.UTC))
                .expiresAt(validTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
                .sign();
    }
}
