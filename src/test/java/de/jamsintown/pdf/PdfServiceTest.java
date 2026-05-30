package de.jamsintown.pdf;

import de.jamsintown.bild.Bild;
import de.jamsintown.story.Story;
import de.jamsintown.text.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfServiceTest {

    @TempDir
    Path tempDir;

    private PdfService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PdfService(null, null, new com.fasterxml.jackson.databind.ObjectMapper());
        setCapturesPath(service, tempDir.toString() + "/");
    }

    private void setCapturesPath(PdfService s, String path) throws Exception {
        Field f = PdfService.class.getDeclaredField("capturesPath");
        f.setAccessible(true);
        f.set(s, path);
    }

    private Story story(String name, String layout) {
        Story s = new Story();
        s.name = name;
        s.layout = layout;
        return s;
    }

    private Bild bild(String pfad, String title) {
        Bild b = new Bild();
        b.pfad = pfad;
        b.setTitle(title);
        b.storyColumn = 0;
        b.storyPosition = 0;
        return b;
    }

    private Text text(String title, String description) {
        Text t = new Text();
        t.title = title;
        t.description = description;
        t.storyColumn = 0;
        t.storyPosition = 1;
        return t;
    }

    private boolean isPdf(byte[] bytes) {
        return bytes != null && bytes.length > 4
            && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    @Test
    void renderPdf_keineStories_liefertGueltigesPdf() {
        byte[] result = service.renderPdf(List.of(), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_eineStory_liefertGueltigesPdf() {
        var sd = new PdfService.StoryData(story("Testgeschichte", "2col"), List.of(), List.of(), Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(sd), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_einspaltigesLayout_keinFehler() {
        var sd = new PdfService.StoryData(story("1col Story", "1col"),
            List.of(bild("/nichtvorhanden.jpg", "Bild")),
            List.of(text("Überschrift", "Inhalt")),
            Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(sd), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_zweispaltigesLayout_keinFehler() {
        Bild linkes = bild("/links.jpg", "Links");
        linkes.storyColumn = 0;
        Bild rechtes = bild("/rechts.jpg", "Rechts");
        rechtes.storyColumn = 1;
        var sd = new PdfService.StoryData(story("2col Story", "2col"), List.of(linkes, rechtes), List.of(), Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(sd), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_mitDeckblatt_liefertGueltigesPdf() {
        var sd = new PdfService.StoryData(story("Story", "1col"), List.of(), List.of(), Map.of(), Map.of());
        PdfOptions options = new PdfOptions(null, false, false, true, "Mein Jahrbuch 2025", false, false, false, 1, 5);
        byte[] result = service.renderPdf(List.of(sd), options);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_mitLeeresDeckblattTitle_verwendetFallback() {
        var sd = new PdfService.StoryData(story("Story", "1col"), List.of(), List.of(), Map.of(), Map.of());
        PdfOptions options = new PdfOptions(null, false, false, true, "  ", false, false, false, 1, 5);
        assertDoesNotThrow(() -> service.renderPdf(List.of(sd), options));
    }

    @Test
    void renderPdf_mitSeitenzahlen_liefertGueltigesPdf() {
        var sd = new PdfService.StoryData(story("Story", "1col"), List.of(), List.of(), Map.of(), Map.of());
        PdfOptions options = new PdfOptions(null, false, false, false, null, true, false, false, 1, 5);
        byte[] result = service.renderPdf(List.of(sd), options);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_pendingSektion_nullStory_keinFehler() {
        var pending = new PdfService.StoryData(null,
            List.of(bild("/irgendein.jpg", "Bild ohne Story")),
            List.of(text("Text ohne Story", "Beschreibung")),
            Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(pending), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_bildDateiNichtVorhanden_liefertTrotzdemPdf() {
        var sd = new PdfService.StoryData(story("Story", "1col"),
            List.of(bild("/nichtvorhanden.jpg", "Fehlendes Bild")), List.of(), Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(sd), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_bildDateiVorhanden_wirdEingebettet() throws Exception {
        Path img = tempDir.resolve("test.jpg");
        Files.write(img, "FAKE_IMAGE_DATA".getBytes(StandardCharsets.UTF_8));
        var sd = new PdfService.StoryData(story("Story", "1col"),
            List.of(bild("/test.jpg", "Vorhandenes Bild")), List.of(), Map.of(), Map.of());
        assertDoesNotThrow(() -> service.renderPdf(List.of(sd), null));
    }

    @Test
    void renderPdf_mehrerStories_jeweilsEigeneSeitenbreak() {
        var sd1 = new PdfService.StoryData(story("Story 1", "1col"), List.of(), List.of(), Map.of(), Map.of());
        var sd2 = new PdfService.StoryData(story("Story 2", "1col"), List.of(), List.of(), Map.of(), Map.of());
        var sd3 = new PdfService.StoryData(story("Story 3", "1col"), List.of(), List.of(), Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(sd1, sd2, sd3), null);
        assertTrue(isPdf(result));
        assertTrue(result.length > 1000, "Mehrseitiges PDF sollte größer sein");
    }

    @Test
    void renderPdf_scrapbookLayout_keinFehler() {
        Bild hero = bild("/hero.jpg", "Hero");
        hero.hauptbild = true;
        var sd = new PdfService.StoryData(story("Scrapbook Story", "scrapbook"),
            List.of(hero, bild("/a.jpg", "A"), bild("/b.jpg", "B")), List.of(), Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(sd), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_gridLayout_dreiSpalten_keinFehler() {
        Bild b0 = bild("/a.jpg", "A"); b0.storyColumn = 0;
        Bild b1 = bild("/b.jpg", "B"); b1.storyColumn = 1;
        Bild b2 = bild("/c.jpg", "C"); b2.storyColumn = 2;
        var sd = new PdfService.StoryData(story("Grid Story", "grid"), List.of(b0, b1, b2), List.of(), Map.of(), Map.of());
        byte[] result = service.renderPdf(List.of(sd), null);
        assertTrue(isPdf(result));
    }

    @Test
    void renderPdf_alleOptionen_kombiniert() {
        var sd = new PdfService.StoryData(story("Story", "2col"),
            List.of(bild("/bild.jpg", "Titel")),
            List.of(text("Text", "Inhalt")),
            Map.of(), Map.of());
        PdfOptions options = new PdfOptions(List.of(1L), false, false, true, "Jahrbuch 2025", true, true, true, 1, 5);
        byte[] result = service.renderPdf(List.of(sd), options);
        assertTrue(isPdf(result));
    }
}
