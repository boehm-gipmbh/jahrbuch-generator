package de.jamsintown.capture;

import de.jamsintown.bild.Bild;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (setImageSettings(imageSettings)) {
            return getBildUni();
        } else {
            return Uni.createFrom().failure(new RuntimeException("Failed to set image settings"));
        }
    }

    private boolean setImageSettings(ImageSettings imageSettings) {
        return setImageSetting(imageSettings.mainImgsettingsImageformat);
    }

    private boolean setImageSetting(String mainImgsettingsImageformat) {
        try {
            // Befehl definieren
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", "gphoto2 --set-config='/main/imgsettings/imageformat=" + mainImgsettingsImageformat + "' --debug --debug-loglevel=\"error\"");

            // Prozess starten
            Process process = processBuilder.start();

            // Ausgabe lesen
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Warten, bis der Prozess beendet ist
            int exitCode = process.waitFor();
            System.out.println("Exit-Code: " + exitCode);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Uni<Bild> getBildUni() {
        try {
            // Befehl definieren
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", "gphoto2 --capture-image-and-download --debug --debug-loglevel=\"error\"");

            // Prozess starten
            Process process = processBuilder.start();

            String originalPath = null;
            String fileName = null;
            // Ausgabe lesen
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if ((originalPath = extractPath(line)) != null) {
                        break;
                    }
                }
                // Warten, bis der Prozess beendet ist
                int exitCode = process.waitFor();
                System.out.println("Exit-Code: " + exitCode);
                final String capturedPath = originalPath;
                if (capturedPath == null) {
                    return Uni.createFrom().failure(new RuntimeException("Konnte kein Bild aufnehmen"));
                }
                // Dateinamen aus dem Pfad extrahieren
                fileName = Paths.get(capturedPath).getFileName().toString();

                // Neuen Pfad im konfigurierten Verzeichnis erstellen
                String targetPath = Paths.get(capturesPath, fileName).toString();

                // Verzeichnis erstellen und Datei kopieren
//                Files.createDirectories(Paths.get(capturesPath));
//                Files.copy(Paths.get(capturedPath), Paths.get(targetPath),
//                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                byte[] bytes = Files.readAllBytes(Paths.get(originalPath));
                if (bytes != null) {
                    System.out.println("Bildgröße: " + bytes.length + " Bytes");
                } else {
                    System.out.println("Keine Bilddaten vorhanden!");
                }
                //  Buffer buffer = Buffer.buffer(bytes);
                //  bild.data = bytes;

                // Reaktive Verarbeitung ohne blockierenden Aufruf
                String finalFileName = fileName;
                return userService.getCurrentUser()
                        .map(user -> {
                            Bild bild = new Bild();
                            bild.created = ZonedDateTime.now();
                            bild.pfad = "/" + finalFileName;
                            bild.description = "Bild von " + user.name + " aufgenommen";
                            bild.title = "Bild mit Titel " + finalFileName;
                            bild.priority = 2;  // set default priority to 2 (yellow)
                            bild.user = user;
                            return bild;
                        })
                        .chain(bild -> {
                            try {
                                return bild.persistAndFlush();
                            } catch (Exception e) {
                                System.err.println("Fehler beim Speichern des Bildes: " + e.getMessage());
                                return Uni.createFrom().failure(e);
                            }
                        });
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }


    public String extractPath(String input) {
        // Regex definieren
        String regex = "Speichere Datei als (/.+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        // Überprüfen, ob der Regex passt
        if (matcher.find()) {
            return matcher.group(1); // Extrahierter Teilstring
        }
        return null; // Rückgabe von null, falls kein Match gefunden wird
    }

}
