package de.jamsintown.video;

import de.jamsintown.story.Story;
import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
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
            @Override
            @SuppressWarnings("unchecked")
            public <T extends io.quarkus.hibernate.reactive.panache.PanacheEntityBase> Uni<T> persistAndFlush() {
                return Uni.createFrom().item((T) this);
            }
        };
        video.pfad = pfad;
        return video;
    }

    private Video videoStubWithStory(String pfad, String storyName) {
        Video video = videoStub(pfad);
        Story story = new Story();
        story.name = storyName;
        video.story = story;
        return video;
    }

    private UserService userServiceStub() {
        return new UserService(null, null) {
            @Override
            public Uni<User> getCurrentUser() {
                return Uni.createFrom().item(new User());
            }
        };
    }

    private MinioService minioStub() {
        return new MinioService() {
            @Override public Uni<Void> delete(String objectKey) { return Uni.createFrom().voidItem(); }
        };
    }

    private VideoService serviceWithVideo(Video video) throws Exception {
        VideoService s = new VideoService(null, minioStub()) {
            @Override
            protected Uni<Video> findByIdIncludeDeleted(Long id) {
                return Uni.createFrom().item(video);
            }
        };
        setCapturesPath(s, tempDir.toString() + "/");
        return s;
    }

    private VideoService serviceWithVideoForSoftDelete(Video video) throws Exception {
        VideoService s = new VideoService(null, minioStub()) {
            @Override
            public Uni<Video> findById(Long id) {
                return Uni.createFrom().item(video);
            }
        };
        setCapturesPath(s, tempDir.toString() + "/");
        return s;
    }

    private VideoService serviceWithVideoForRestore(Video video) throws Exception {
        VideoService s = new VideoService(userServiceStub(), minioStub()) {
            @Override
            protected Uni<Video> findByIdIncludeDeleted(Long id) {
                return Uni.createFrom().item(video);
            }
        };
        setCapturesPath(s, tempDir.toString() + "/");
        return s;
    }

    private VideoService serviceWithNotFound(long id) throws Exception {
        VideoService s = new VideoService(null, minioStub()) {
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

    // ── softDelete ───────────────────────────────────────────────────────────

    @Test
    void softDelete_mitStory_speichertStoryNameUndLeertStory() throws Exception {
        Video video = videoStubWithStory("/video.mp4", "Hochzeitsfeier");
        VideoService s = serviceWithVideoForSoftDelete(video);

        s.softDelete(1L).await().indefinitely();

        assertEquals("Hochzeitsfeier", video.deletedFromStoryName,
                "deletedFromStoryName muss den ursprünglichen Story-Namen speichern");
        assertNull(video.story, "story muss nach softDelete null sein");
        assertTrue(video.deleted, "deleted-Flag muss gesetzt sein");
    }

    @Test
    void softDelete_ohneStory_deletedFromStoryNameBleibtNull() throws Exception {
        Video video = videoStub("/video.mp4");
        VideoService s = serviceWithVideoForSoftDelete(video);

        s.softDelete(1L).await().indefinitely();

        assertNull(video.deletedFromStoryName, "deletedFromStoryName darf bei fehlendem Story nicht gesetzt werden");
        assertTrue(video.deleted);
    }

    // ── restore ──────────────────────────────────────────────────────────────

    @Test
    void restore_ohneVorigeStory_setzt_deletedFlagZurueck() throws Exception {
        Video video = videoStub("/video.mp4");
        video.deleted = true;
        video.deletedFromStoryName = null;
        VideoService s = serviceWithVideoForRestore(video);

        s.restore(1L).await().indefinitely();

        assertFalse(video.deleted, "deleted-Flag muss nach restore zurückgesetzt sein");
        assertNull(video.deletedFromStoryName, "deletedFromStoryName muss geleert sein");
    }
}
