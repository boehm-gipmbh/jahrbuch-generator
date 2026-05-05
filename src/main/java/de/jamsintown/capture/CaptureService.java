package de.jamsintown.capture;

import de.jamsintown.bild.Bild;
import de.jamsintown.bild.ExifUtils;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class CaptureService {

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String capturesPath;

    private final UserService userService;

    @Inject
    public CaptureService(UserService userService) {
        this.userService = userService;
    }

    public Uni<Bild> create(ImageSettings imageSettings) {
        return userService.getCurrentUser().chain(user -> createForUser(imageSettings, user, null));
    }

    public Uni<Bild> createForUser(ImageSettings imageSettings, User user) {
        return createForUser(imageSettings, user, null);
    }

    public Uni<Bild> createForUser(ImageSettings imageSettings, User user, Gruppe gruppe) {
        if (setImageSettings(imageSettings)) {
            return getBildUniForUser(user, gruppe);
        } else {
            return Uni.createFrom().failure(new RuntimeException("Failed to set image settings"));
        }
    }

    private boolean setImageSettings(ImageSettings imageSettings) {
        return setImageSetting(imageSettings.mainImgsettingsImageformat);
    }

    private boolean setImageSetting(String mainImgsettingsImageformat) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", "gphoto2 --set-config='/main/imgsettings/imageformat=" + mainImgsettingsImageformat + "' --debug --debug-loglevel=\"error\"");

            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }

            int exitCode = process.waitFor();
            log.info("Exit-Code: {}", exitCode);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    private Uni<Bild> getBildUniForUser(User user, Gruppe gruppe) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", "gphoto2 --capture-image-and-download --debug --debug-loglevel=\"error\"");

            Process process = processBuilder.start();

            String originalPath = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                    if ((originalPath = extractPath(line)) != null) {
                        break;
                    }
                }
                int exitCode = process.waitFor();
                log.info("Exit-Code: {}", exitCode);
                final String capturedPath = originalPath;
                if (capturedPath == null) {
                    return Uni.createFrom().failure(new RuntimeException("Konnte kein Bild aufnehmen"));
                }
                String fileName = Paths.get(capturedPath).getFileName().toString();

                // Gruppe bestimmen: explizit übergeben > activeGroup des Users > ungrouped
                Gruppe effectiveGruppe = gruppe != null ? gruppe : user.activeGroup;
                String subDir = (effectiveGruppe != null)
                        ? "gruppen/" + effectiveGruppe.id + "/"
                        : "ungrouped/";

                String finalFileName = fileName;
                Gruppe finalGruppe = effectiveGruppe;
                return Uni.createFrom().item(user).chain(u -> {
                    Path targetPath = null;
                    try {
                        Path targetDir = Paths.get(capturesPath, subDir);
                        Files.createDirectories(targetDir);
                        targetPath = targetDir.resolve(finalFileName);
                        Files.move(Paths.get(capturedPath), targetPath,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.warn("Konnte Bild nicht ins Gruppenverzeichnis verschieben: {}", e.getMessage());
                    }
                    ZonedDateTime capturedAt = targetPath != null ? ExifUtils.readCapturedAt(targetPath) : null;
                    Bild bild = new Bild();
                    bild.created = ZonedDateTime.now();
                    bild.capturedAt = capturedAt != null ? capturedAt : bild.created;
                    bild.pfad = "/" + subDir + finalFileName;
                    bild.description = "Bild von " + u.name + " aufgenommen";
                    bild.title = "Bild mit Titel " + finalFileName;
                    bild.priority = 2;
                    bild.user = u;
                    bild.group = finalGruppe;
                    return Uni.createFrom().item(bild);
                }).chain(bild -> bild.persistAndFlush()
                        .replaceWith(bild)
                        .invoke(b -> log.info("Bild in der DB gespeichert mit ID: {}", bild.id))
                        .onFailure().invoke(e -> log.error("Fehler beim Persistieren: {}", e.getMessage(), e)));
            }
        } catch (InterruptedException | IOException e) {
            log.error("Fehler beim Capture: {}", e.getMessage(), e);
            return Uni.createFrom().failure(e);
        }
    }

    public Path captureToLocalFile(ImageSettings imageSettings, long groupId, String capturesPath) throws Exception {
        if (!setImageSettings(imageSettings)) {
            throw new RuntimeException("Kamera-Einstellungen konnten nicht gesetzt werden");
        }
        ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                "gphoto2 --capture-image-and-download --debug --debug-loglevel=\"error\"");
        Process process = pb.start();
        String capturedPath = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
                if ((capturedPath = extractPath(line)) != null) break;
            }
            process.waitFor();
        }
        if (capturedPath == null) throw new RuntimeException("Keine Bilddatei aufgenommen");

        String fileName = Paths.get(capturedPath).getFileName().toString();
        String subDir = "gruppen/" + groupId + "/";
        Path targetDir = Paths.get(capturesPath, subDir);
        Files.createDirectories(targetDir);
        Path targetPath = targetDir.resolve(fileName);
        Files.move(Paths.get(capturedPath), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Bild lokal gespeichert: {}", targetPath);
        return targetPath;
    }

    public String extractPath(String input) {
        String regex = "(?:Speichere Datei als|Saving file as) (.+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
