package de.jamsintown.bild;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
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

    @BeforeEach
    void setUp() throws Exception {
        resource = new ExterneBilderResource();

        // capturesPath manuell setzen
        Field capturesPathField = ExterneBilderResource.class.getDeclaredField("capturesPath");
        capturesPathField.setAccessible(true);
        capturesPathField.set(resource, tempDir.toString());

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
        Response response = resource.getBild("testbild.jpg");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof File);
    }

    @Test
    void getBild_nichtExistierendeDatei_liefert404() {
        Response response = resource.getBild("nichtvorhanden.jpg");

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("Datei nicht gefunden", response.getEntity());
    }

    @Test
    void getBild_unterverzeichnis_liefertOkResponse() {
        Response response = resource.getBild("verzeichnis/unterbild.png");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof File);
    }

    @Test
    void getBild_pathTraversal_liefert403() {
        Response response = resource.getBild("../../../etc/passwd");

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals("Zugriff verweigert", response.getEntity());
    }

    @Test
    void getBild_dateiMitLeerzeichen_kodiertFilenameKorrekt() {
        Response response = resource.getBild("datei mit leerzeichen.txt");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        MultivaluedMap<String, Object> headers = response.getMetadata();
        Object contentDisposition = headers.getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.toString().contains("filename=\"datei%20mit%20leerzeichen.txt\""));
    }

    @Test
    void getBild_cacheControlHeader_wirdKorrektGesetzt() {
        Response response = resource.getBild("testbild.jpg");

        MultivaluedMap<String, Object> headers = response.getMetadata();
        Object cacheControl = headers.getFirst("Cache-Control");
        assertNotNull(cacheControl);
        assertEquals("public, max-age=86400", cacheControl.toString());
    }

    @Test
    void getBild_contentTypeHeader_wirdGesetzt() {
        Response response = resource.getBild("testbild.jpg");

        MultivaluedMap<String, Object> headers = response.getMetadata();
        assertNotNull(headers.getFirst("Content-Type"));
    }
}