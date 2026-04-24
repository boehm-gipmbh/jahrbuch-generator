package de.jamsintown.fotobox;

import de.jamsintown.bild.Bild;
import de.jamsintown.dtos.FotoboxConfigDTO;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.ZonedDateTime;

@ApplicationScoped
public class FotoboxDbService {

    private final UserService userService;

    @Inject
    public FotoboxDbService(UserService userService) {
        this.userService = userService;
    }

    @WithSession
    public Uni<FotoboxConfigDTO> getConfig(long groupId) {
        return Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Gruppe nicht gefunden"))
                .map(g -> new FotoboxConfigDTO(g.id, g.name, "Small Fine JPEG"));
    }

    @WithTransaction
    public Uni<Bild> saveBildForGroup(byte[] imageBytes, String filename, long groupId, String capturesPath, String captureUserName) {
        String subDir = "gruppen/" + groupId + "/";
        String pfad = "/" + subDir + filename;

        try {
            java.nio.file.Path targetDir = java.nio.file.Paths.get(capturesPath, subDir);
            java.nio.file.Files.createDirectories(targetDir);
            java.nio.file.Files.write(targetDir.resolve(filename), imageBytes);
        } catch (Exception e) {
            return Uni.createFrom().failure(new RuntimeException("Datei konnte nicht gespeichert werden: " + e.getMessage()));
        }

        return userService.findByName(captureUserName)
                .onItem().ifNull().switchTo(() -> userService.createFotoboxUser(captureUserName))
                .chain(user -> Gruppe.<Gruppe>findById(groupId)
                        .onItem().ifNull().failWith(() -> new NotFoundException("Gruppe nicht gefunden"))
                        .chain(gruppe -> {
                            Bild bild = new Bild();
                            bild.created = ZonedDateTime.now();
                            bild.pfad = pfad;
                            bild.description = "Fotobox-Aufnahme";
                            bild.title = filename;
                            bild.priority = 2;
                            bild.user = user;
                            bild.group = gruppe;
                            return bild.persistAndFlush();
                        }));
    }
}
