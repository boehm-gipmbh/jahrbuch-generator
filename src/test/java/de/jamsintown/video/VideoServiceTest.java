package de.jamsintown.video;

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

class VideoServiceTest {

    @TempDir
    Path tempDir;

    private VideoService service;

    @BeforeEach
    void setUp() throws Exception {
        service = serviceWithVideo(videoStub("/dummy.mp4"));
    }

    private void setCapturesPath(VideoService s, String path) throws Exception {
        Field field = VideoService.class.getDeclaredField("capturesPath");
        field.setAccessible(true);
        field.set(s, path);
    }

    private Video videoStub(String pfad) {
        Video video = new Video() {
            @Override
            public Uni<Void> delete() {
                return Uni.createFrom().voidItem();
            }
        };
        video.pfad = pfad;
        return video;
    }

    private VideoService serviceWithVideo(Video video) throws Exception {
        VideoService s = new VideoService(null) {
            @Override
            protected Uni<Video> findByIdIncludeDeleted(Long id) {
                return Uni.createFrom().item(video);
            }
        };
        setCapturesPath(s, tempDir.toString() + "/");
        return s;
    }

    private VideoService serviceWithNotFound(long id) throws Exception {
        VideoService s = new VideoService(null) {
            @Override
            protected Uni<Video> findByIdIncludeDeleted(Long videoId) {
                return Uni.createFrom().failure(new ObjectNotFoundException(videoId, "Video"));
            }
        };
        setCapturesPath(s, tempDir.toString() + "/");
        return s;
    }

    @Test
    void delete_vorhandeneDatei_wirdVonFestplatteLöschung() throws Exception {
        Path datei = tempDir.resolve("video.mp4");
        Files.write(datei, "Videodaten".getBytes(StandardCharsets.UTF_8));
        assertTrue(Files.exists(datei));

        VideoService s = serviceWithVideo(videoStub("/video.mp4"));
        s.hardDelete(1L).await().indefinitely();

        assertFalse(Files.exists(datei), "Video-Datei muss von Festplatte gelöscht worden sein");
    }

    @Test
    void delete_vorhhandenerThumbnail_wirdMitGelöscht() throws Exception {
        Path datei = tempDir.resolve("video.mp4");
        Path thumb = tempDir.resolve("video.mp4.thumb.jpg");
        Files.write(datei, "Videodaten".getBytes(StandardCharsets.UTF_8));
        Files.write(thumb, "Thumbdaten".getBytes(StandardCharsets.UTF_8));

        VideoService s = serviceWithVideo(videoStub("/video.mp4"));
        s.hardDelete(1L).await().indefinitely();

        assertFalse(Files.exists(datei), "Video-Datei muss gelöscht worden sein");
        assertFalse(Files.exists(thumb), "Thumbnail muss mit gelöscht worden sein");
    }

    @Test
    void delete_nichtVorhandeneDatei_keinFehler() throws Exception {
        VideoService s = serviceWithVideo(videoStub("/nichtvorhanden.mp4"));

        assertDoesNotThrow(() -> s.hardDelete(1L).await().indefinitely());
    }

    @Test
    void delete_pfadMitFührendemSlash_wirdKorrektAufgelöst() throws Exception {
        Path datei = tempDir.resolve("unterordner/video.mp4");
        Files.createDirectories(datei.getParent());
        Files.write(datei, "Daten".getBytes(StandardCharsets.UTF_8));

        VideoService s = serviceWithVideo(videoStub("/unterordner/video.mp4"));
        s.hardDelete(1L).await().indefinitely();

        assertFalse(Files.exists(datei), "Datei in Unterordner muss gelöscht worden sein");
    }

    @Test
    void delete_nichtGefunden_wirftObjectNotFoundException() throws Exception {
        VideoService s = serviceWithNotFound(99L);

        assertThrows(ObjectNotFoundException.class,
                () -> s.hardDelete(99L).await().indefinitely());
    }
}
