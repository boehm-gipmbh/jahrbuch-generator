package de.jamsintown.bild;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExterneBilderResourceTest {

    private ExterneBilderResource resource;

    @TempDir
    Path tempDir;

    // BildService-Stub, der Eigentümerschaft für alle Pfade erlaubt
    private static final BildService AUTHORIZED_SERVICE = new BildService(null) {
        @Override
        public Uni<Bild> findByPfad(String pfad) {
            return Uni.createFrom().item(new Bild());
        }
    };

    // BildService-Stub, der Eigentümerschaft für alle Pfade verweigert
    private static final BildService UNAUTHORIZED_SERVICE = new BildService(null) {
        @Override
        public Uni<Bild> findByPfad(String pfad) {
            return Uni.createFrom().failure(new ObjectNotFoundException((java.io.Serializable) pfad, "Bild"));
        }
    };

    private ExterneBilderResource resourceFor(BildService service, String path) throws Exception {
        ExterneBilderResource r = new ExterneBilderResource(service);
        Field f = ExterneBilderResource.class.getDeclaredField("defaultCapturesPath");
        f.setAccessible(true);
        f.set(r, path + "/");
        return r;
    }

    @BeforeEach
    void setUp() throws Exception {
        resource = resourceFor(AUTHORIZED_SERVICE, tempDir.toString());

        // Testdateien erstellen
        createTestFile("testbild.jpg", "JPEG-Testdaten");
        createTestFile("testdokument.pdf", "PDF-Testdaten");
        createTestFile("verzeichnis/unterbild.png", "PNG-Testdaten");
        createTestFile("datei mit leerzeichen.txt", "Textdaten");
    }

    private void createTestFile(String relativePath, String content) throws Exception {
        Path filePath = tempDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getBild_existierendeDatei_liefertOkResponse() {
        Response response = resource.getBild("testbild.jpg", false).await().indefinitely();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof File);
    }

    @Test
    void getBild_nichtExistierendeDatei_liefert404() {
        Response response = resource.getBild("nichtvorhanden.jpg", false).await().indefinitely();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("Datei nicht gefunden", response.getEntity());
    }

    @Test
    void getBild_unterverzeichnis_liefertOkResponse() {
        Response response = resource.getBild("verzeichnis/unterbild.png", false).await().indefinitely();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof File);
    }

    @Test
    void getBild_pathTraversal_liefert403() {
        Response response = resource.getBild("../../../etc/passwd", false).await().indefinitely();

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals("Zugriff verweigert", response.getEntity());
    }

    @Test
    void getBild_nichtAuthorisiert_liefert403() throws Exception {
        ExterneBilderResource unauthorizedResource =
                resourceFor(UNAUTHORIZED_SERVICE, tempDir.toString());

        Response response = unauthorizedResource.getBild("testbild.jpg", false).await().indefinitely();

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals("Zugriff verweigert", response.getEntity());
    }

    @Test
    void getBild_dateiMitLeerzeichen_kodiertFilenameKorrekt() {
        Response response = resource.getBild("datei mit leerzeichen.txt", false).await().indefinitely();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        MultivaluedMap<String, Object> headers = response.getMetadata();
        Object contentDisposition = headers.getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.toString().contains("filename=\"datei%20mit%20leerzeichen.txt\""));
    }

    @Test
    void getBild_cacheControlHeader_wirdKorrektGesetzt() {
        Response response = resource.getBild("testbild.jpg", false).await().indefinitely();

        MultivaluedMap<String, Object> headers = response.getMetadata();
        Object cacheControl = headers.getFirst("Cache-Control");
        assertNotNull(cacheControl);
        assertEquals("no-cache", cacheControl.toString());
    }

    @Test
    void getBild_contentTypeHeader_wirdGesetzt() {
        Response response = resource.getBild("testbild.jpg", false).await().indefinitely();

        MultivaluedMap<String, Object> headers = response.getMetadata();
        assertNotNull(headers.getFirst("Content-Type"));
    }
}