package de.jamsintown.capture;

import de.jamsintown.bild.Bild;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class CaptureService {


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

            // Ausgabe lesen
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String path = null;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if ((path = extractPath(line)) != null) {
                    break;
                }
            }

            // Warten, bis der Prozess beendet ist
            int exitCode = process.waitFor();
            System.out.println("Exit-Code: " + exitCode);
            Bild bild = new Bild();
            bild.created = ZonedDateTime.now();
            bild.pfad = path;
            bild.description = "Bild von Kamera";

            return userService.getCurrentUser().chain(user -> {
                bild.user = user;
                return bild.persistAndFlush();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
