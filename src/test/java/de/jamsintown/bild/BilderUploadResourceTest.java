package de.jamsintown.bild;

import de.jamsintown.config.AppConfigService;
import de.jamsintown.story.StoryService;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.reactive.server.multipart.FileItem;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BilderUploadResourceTest {

    @TempDir
    Path tempDir;

    private BilderUploadResource buildResource(User user) {
        AppConfigService appConfig = new AppConfigService() {
            @Override
            public Uni<String> getValue(String key) {
                return switch (key) {
                    case "jahrbuch.upload.max-size" -> Uni.createFrom().item("10485760");
                    case "jahrbuch.upload.allowed-types" -> Uni.createFrom().item(".jpg,.jpeg,.png");
                    case "jahrbuch.captures.path" -> Uni.createFrom().item(tempDir.toString() + "/");
                    default -> Uni.createFrom().item((String) null);
                };
            }
        };

        BildService bildService = new BildService(null) {
            @Override
            public Uni<Bild> create(Bild bild) {
                return Uni.createFrom().item(bild);
            }
        };

        UserService userService = new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(user);
            }
        };

        return new BilderUploadResource(bildService, new StoryService(null), userService,
                null, appConfig, tempDir.toString() + "/") {
            @Override
            void generateThumbnail(Path path) {
                // ImageMagick nicht verfügbar im Test — Thumbnail-Generierung überspringen
            }
            @Override
            protected <T> io.smallrye.mutiny.Uni<T> executeBlockingFileIO(java.util.concurrent.Callable<T> callable) {
                try {
                    return io.smallrye.mutiny.Uni.createFrom().item(callable.call());
                } catch (Exception e) {
                    return io.smallrye.mutiny.Uni.createFrom().failure(e);
                }
            }
        };
    }

    private MultipartFormDataInput buildInput(String fileName) {
        byte[] fakeBytes = "FAKE_IMAGE_DATA".getBytes();

        FileItem fileItem = new FileItem() {
            @Override public boolean isInMemory() { return true; }
            @Override public Path getFile() { return null; }
            @Override public long getFileSize() { return fakeBytes.length; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(fakeBytes); }
            @Override public void delete() {}
            @Override public void write(Path target) throws IOException {
                Files.write(target, fakeBytes);
            }
        };

        FormValue fileValue = new FormValue() {
            @Override public String getValue() { return null; }
            @Override public String getCharset() { return null; }
            @Override public FileItem getFileItem() { return fileItem; }
            @Override public boolean isFileItem() { return true; }
            @Override public String getFileName() { return fileName; }
            @Override public MultivaluedMap<String, String> getHeaders() { return new MultivaluedHashMap<>(); }
        };

        return () -> Map.of("file", (Collection<FormValue>) List.of(fileValue));
    }

    @Test
    void upload_mitAktiverGruppe_speichertInGruppenUnterordner() throws Exception {
        Gruppe gruppe = new Gruppe();
        gruppe.id = 42L;

        User user = new User();
        user.activeGroup = gruppe;

        Bild result = buildResource(user).uploadBild(buildInput("foto.jpg")).await().indefinitely();

        assertNotNull(result);
        assertTrue(result.getPfad().startsWith("/gruppen/42/"),
                "Pfad muss /gruppen/42/... sein, war: " + result.getPfad());
        assertTrue(Files.isDirectory(tempDir.resolve("gruppen/42")),
                "Verzeichnis gruppen/42 muss angelegt worden sein");
    }

    @Test
    void upload_ohneAktiveGruppe_speichertInUngrouped() {
        User user = new User();
        user.activeGroup = null;

        Bild result = buildResource(user).uploadBild(buildInput("foto.jpg")).await().indefinitely();

        assertNotNull(result);
        assertTrue(result.getPfad().startsWith("/ungrouped/"),
                "Pfad muss /ungrouped/... sein, war: " + result.getPfad());
        assertTrue(Files.isDirectory(tempDir.resolve("ungrouped")),
                "Verzeichnis ungrouped muss angelegt worden sein");
    }

    @Test
    void upload_pfadEnthältDateiname() {
        User user = new User();
        user.activeGroup = null;

        Bild result = buildResource(user).uploadBild(buildInput("foto.jpg")).await().indefinitely();

        assertNotNull(result);
        assertTrue(result.getPfad().endsWith(".jpg"),
                "Pfad muss auf .jpg enden, war: " + result.getPfad());
    }
}
