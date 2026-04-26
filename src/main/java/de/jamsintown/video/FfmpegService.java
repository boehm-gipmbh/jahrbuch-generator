package de.jamsintown.video;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@ApplicationScoped
public class FfmpegService {

    private static final String THUMB_SUFFIX = ".thumb.jpg";

    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Transkodiert zu H.264 + faststart. Ersetzt die Originaldatei.
     * Gibt true zurück wenn erfolgreich, false wenn fehlgeschlagen.
     */
    public boolean processVideo(Path videoPath) {
        Path tmpPath = videoPath.resolveSibling(videoPath.getFileName() + ".processing.mp4");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", videoPath.toString(),
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-movflags", "+faststart",
                    tmpPath.toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            int exit = p.waitFor();
            if (exit == 0) {
                Files.move(tmpPath, videoPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("FFmpeg: {} transkodiert", videoPath.getFileName());
                return true;
            }
            log.error("FFmpeg fehlgeschlagen (exit {}): {}", exit, output);
            return false;
        } catch (Exception e) {
            log.error("FFmpeg Fehler bei {}: {}", videoPath.getFileName(), e.getMessage());
            return false;
        } finally {
            try { Files.deleteIfExists(tmpPath); } catch (Exception ignored) {}
        }
    }

    /**
     * Erzeugt ein Vorschaubild (400px breit) bei Sekunde 1.
     * Dateiname: videoPath + ".thumb.jpg"
     */
    public boolean generateSnapshot(Path videoPath) {
        Path thumbPath = videoPath.resolveSibling(videoPath.getFileName() + THUMB_SUFFIX);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-ss", "00:00:01",
                    "-i", videoPath.toString(),
                    "-frames:v", "1",
                    "-vf", "scale=400:-1",
                    thumbPath.toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            int exit = p.waitFor();
            if (exit == 0) {
                log.info("FFmpeg: Snapshot erzeugt für {}", videoPath.getFileName());
                return true;
            }
            log.warn("FFmpeg Snapshot fehlgeschlagen (exit {}) für {}", exit, videoPath.getFileName());
            return false;
        } catch (Exception e) {
            log.error("FFmpeg Snapshot Fehler bei {}: {}", videoPath.getFileName(), e.getMessage());
            return false;
        }
    }

    public String detectCodec(Path videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "quiet",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name",
                    "-of", "default=noprint_wrappers=1",
                    videoPath.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return output.replace("codec_name=", "").trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static String toSnapshotPfad(String videoPfad) {
        return videoPfad + THUMB_SUFFIX;
    }
}
