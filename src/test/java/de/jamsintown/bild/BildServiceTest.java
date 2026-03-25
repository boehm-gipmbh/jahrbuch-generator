package de.jamsintown.bild;

import io.smallrye.mutiny.Uni;
import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BildServiceTest {

    @TempDir
    Path tempDir;

    private BildService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new BildService(null);
        setCapturesPath(service, tempDir.toString() + "/");
    }

    private void setCapturesPath(BildService s, String path) throws Exception {
        Field field = BildService.class.getDeclaredField("capturesPath");
        field.setAccessible(true);
        field.set(s, path);
    }

    /** Erstellt ein Bild-Stub, dessen delete() kein Panache benötigt */
    private Bild bildStub(String pfad) {
        Bild bild = new Bild() {
            @Override
            public Uni<Void> delete() {
                return Uni.createFrom().voidItem();
            }
        };
        bild.pfad = pfad;
        return bild;
    }

    /** Erstellt einen BildService, der findByIdIncludeDeleted mit einem festen Bild beantwortet */
    private BildService serviceWithBild(Bild bild) throws Exception {
        BildService s = new BildService(null) {
            @Override
            protected Uni<Bild> findByIdIncludeDeleted(Long id) {
                return Uni.createFrom().item(bild);
            }
        };
        setCapturesPath(s, tempDir.toString() + "/");
        return s;
    }

    /** Erstellt einen BildService, der findByIdIncludeDeleted mit einem Fehler beantwortet */
    private BildService serviceWithNotFound(long id) throws Exception {
        BildService s = new BildService(null) {
            @Override
            protected Uni<Bild> findByIdIncludeDeleted(Long bildId) {
                return Uni.createFrom().failure(new ObjectNotFoundException(bildId, "Bild"));
            }
        };
        setCapturesPath(s, tempDir.toString() + "/");
        return s;
    }

    @Test
    void delete_vorhandeneDatei_wirdVonFestplatteLöschung() throws Exception {
        Path datei = tempDir.resolve("testbild.jpg");
        Files.write(datei, "Bilddaten".getBytes(StandardCharsets.UTF_8));
        assertTrue(Files.exists(datei));

        BildService s = serviceWithBild(bildStub("/testbild.jpg"));
        s.hardDelete(1L).await().indefinitely();

        assertFalse(Files.exists(datei), "Datei muss von Festplatte gelöscht worden sein");
    }

    @Test
    void delete_nichtVorhandeneDatei_keinFehler() throws Exception {
        // Datei existiert nicht auf Festplatte — deleteIfExists soll trotzdem nicht werfen
        BildService s = serviceWithBild(bildStub("/nichtvorhanden.jpg"));

        assertDoesNotThrow(() -> s.hardDelete(1L).await().indefinitely());
    }

    @Test
    void delete_pfadMitFührendemSlash_wirdKorrektAufgelöst() throws Exception {
        // Sicherstellen, dass der führende "/" in bild.pfad korrekt entfernt wird
        Path datei = tempDir.resolve("unterordner/bild.jpg");
        Files.createDirectories(datei.getParent());
        Files.write(datei, "Daten".getBytes(StandardCharsets.UTF_8));

        BildService s = serviceWithBild(bildStub("/unterordner/bild.jpg"));
        s.hardDelete(1L).await().indefinitely();

        assertFalse(Files.exists(datei), "Datei in Unterordner muss gelöscht worden sein");
    }

    @Test
    void delete_bildNichtGefunden_wirftObjectNotFoundException() throws Exception {
        BildService s = serviceWithNotFound(42L);

        assertThrows(ObjectNotFoundException.class,
                () -> s.hardDelete(42L).await().indefinitely());
    }
}
