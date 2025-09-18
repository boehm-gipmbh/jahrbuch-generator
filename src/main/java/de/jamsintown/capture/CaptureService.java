package de.jamsintown.capture;

import de.jamsintown.bild.Bild;
import de.jamsintown.config.main.ImageSettings;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.CompletableFuture;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
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
                log.info(line);
            }

            // Warten, bis der Prozess beendet ist
            int exitCode = process.waitFor();
            log.info("Exit-Code: {}", exitCode);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
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
                    log.info(line);
                    if ((originalPath = extractPath(line)) != null) {
                        break;
                    }
                }
                // Warten, bis der Prozess beendet ist
                int exitCode = process.waitFor();
                log.info("Exit-Code: {}", exitCode);
                final String capturedPath = originalPath;
                if (capturedPath == null) {
                    return Uni.createFrom().failure(new RuntimeException("Konnte kein Bild aufnehmen"));
                }
                // Dateinamen aus dem Pfad extrahieren
                fileName = Paths.get(capturedPath).getFileName().toString();
/*
Folgender Code wird nicht benötigt, da das Bild im konfigurierten Verzeichnis gespeichert wird:
Das Verzeichnis ist in /home/dboehm/.gphoto/settings konfiguriert:
        gphoto2=model=Canon EOS M50
        gphoto2=port=usb:001,014
        libgphoto=cached-images=2
        gphoto2=filename=/home/dboehm/jahrbuch-generator/captures/%Y-%m-%d-%H-%M-%S.jpg
 */
 /*
     // Neuen Pfad im konfigurierten Verzeichnis erstellen
                String targetPath = Paths.get(capturesPath, fileName).toString();
                // Verzeichnis erstellen und Datei kopieren
                Files.createDirectories(Paths.get(capturesPath));
                Files.copy(Paths.get(capturedPath), Paths.get(targetPath),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        */
//                byte[] bytes = Files.readAllBytes(Paths.get(originalPath));
//                if (bytes != null) {
//                    System.out.println("Bildgröße: " + bytes.length + " Bytes");
//                } else {
//                    System.out.println("Keine Bilddaten vorhanden!");
//                }
                // Bild in die Cloud hochladen
                uploadToCloudAsync(new File(originalPath),"");


                // Reaktive Verarbeitung ohne blockierenden Aufruf
                String finalFileName = fileName;
                return userService.getCurrentUser().map(user -> {
                    Bild bild = new Bild();
                    bild.created = ZonedDateTime.now();
                    bild.pfad = "/" + finalFileName;
                    bild.description = "Bild von " + user.name + " aufgenommen";
                    bild.title = "Bild mit Titel " + finalFileName;
                    bild.priority = 2;  // set default priority to 2 (yellow)
                    bild.user = user;
                    return bild;
                }).chain(bild -> {
                    try {
                        return bild.persistAndFlush()
                                .replaceWith(bild)
                                .invoke(b -> log.info("Bild in der DB gespeichert mit ID: {}", bild.id))
                                .onFailure().invoke(e -> log.error("Fehler beim Persistieren: {}", e.getMessage(), e));
                    } catch (Exception e) {
                        log.error("Fehler beim Upload in die Cloud: {}", e.getMessage(), e);
                        return Uni.createFrom().failure(e);
                    }
                });
            }
        } catch (InterruptedException | IOException e) {
            log.error("Fehler beim Upload in die Cloud: {}", e.getMessage(), e);
            return Uni.createFrom().failure(e);
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


    /*
    Um den Upload asynchron auszuführen, kannst du in uploadToCloud ein CompletableFuture verwenden.
    So wird der Upload-Request im Hintergrund gesendet und blockiert nicht den Haupt-Thread.
    Beispiel: Die Methode gibt ein CompletableFuture<Void> zurück und wird mit supplyAsync ausgeführt.
     */
    private CompletableFuture<Void> uploadToCloudAsync(File file, String bearerToken) {
        return CompletableFuture.runAsync(() -> {
            try {
                String boundary = "---123";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://jahrbuch-generator.fly.dev/api/v1/bilder/uploadcapture"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                   //     .header("Authorization", bearerToken)
                        .POST(ofMimeMultipartData(file))
                        .build();

                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                log.info("Bild erfolgreich in die Cloud hochgeladen: {}", file.getName());
            } catch (Exception e) {
                log.error("Fehler beim Upload in die Cloud: {}", e.getMessage(), e);
            }
        });
    }


    private void uploadToCloud(File file) throws IOException, InterruptedException {
        String boundary = "---123";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://jahrbuch-generator.fly.dev/api/v1/bilder/uploadcapture"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(ofMimeMultipartData(file))
                .build();

        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /*
     Hilfsmethode, die den Multipart-Body für eine Datei erzeugt.
     Nutz dazu einen ByteArrayOutputStream und setz die Felder und Boundary manuell.
     */
    private static HttpRequest.BodyPublisher ofMimeMultipartData(File file) throws IOException {
        var boundary = "---123";
        var byteArrayOutputStream = new java.io.ByteArrayOutputStream();
        var writer = new java.io.PrintWriter(byteArrayOutputStream, true);

        // -- Boundary und Header
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
        writer.append("Content-Type: application/octet-stream\r\n\r\n");
        writer.flush();

        // Dateiinhalt
        byteArrayOutputStream.write(Files.readAllBytes(file.toPath()));
        writer.append("\r\n").flush();

        // -- Boundary End
        writer.append("--").append(boundary).append("--\r\n");
        writer.flush();

        return HttpRequest.BodyPublishers.ofByteArray(byteArrayOutputStream.toByteArray());
    }


}
