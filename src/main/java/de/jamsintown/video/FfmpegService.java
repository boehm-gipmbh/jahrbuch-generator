package de.jamsintown.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    "-vf", "scale='min(1280,iw)':-2",
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

    public ZonedDateTime readCreationTime(Path videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "quiet",
                    "-print_format", "default=noprint_wrappers=1",
                    "-show_entries", "format_tags=creation_time",
                    videoPath.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String prefix = "TAG:creation_time=";
            if (output.startsWith(prefix)) {
                String iso = output.substring(prefix.length()).trim();
                return Instant.parse(iso).atZone(ZoneId.systemDefault());
            }
        } catch (Exception e) {
            log.debug("creation_time nicht lesbar für {}: {}", videoPath.getFileName(), e.getMessage());
        }
        return null;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ISO6709 = Pattern.compile("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)([+-]\\d+\\.?\\d*)?");

    public String readMetadataJson(Path videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "quiet",
                    "-print_format", "json",
                    "-show_format", "-show_streams",
                    videoPath.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return buildMetadataJson(output);
        } catch (Exception e) {
            log.debug("Metadaten nicht lesbar für {}: {}", videoPath.getFileName(), e.getMessage());
            return null;
        }
    }

    private String buildMetadataJson(String ffprobeJson) {
        try {
            JsonNode root = MAPPER.readTree(ffprobeJson);
            Map<String, Object> result = new LinkedHashMap<>();

            JsonNode tags = root.path("format").path("tags");

            String creationTime = textOrNull(tags, "creation_time");
            if (creationTime != null) result.put("creationTime", creationTime);

            String make = firstNonNull(tags, "com.apple.quicktime.make", "make");
            if (make != null) result.put("make", make);

            String model = firstNonNull(tags, "com.apple.quicktime.model", "model");
            if (model != null) result.put("model", model);

            String location = firstNonNull(tags, "com.apple.quicktime.location.ISO6709", "location");
            if (location != null) parseIso6709(location, result);

            String durationStr = root.path("format").path("duration").asText(null);
            if (durationStr != null) {
                try { result.put("duration", Math.round(Double.parseDouble(durationStr))); }
                catch (NumberFormatException ignored) {}
            }

            for (JsonNode stream : root.path("streams")) {
                if (!"video".equals(stream.path("codec_type").asText())) continue;
                String codec = stream.path("codec_name").asText(null);
                if (codec != null) result.put("codec", codec);
                int w = stream.path("width").asInt(0), h = stream.path("height").asInt(0);
                if (w > 0 && h > 0) { result.put("width", w); result.put("height", h); }
                String fps = stream.path("r_frame_rate").asText(null);
                if (fps != null && fps.contains("/")) {
                    String[] parts = fps.split("/");
                    try {
                        double fpsVal = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
                        result.put("fps", Math.round(fpsVal * 100.0) / 100.0);
                    } catch (NumberFormatException ignored) {}
                }
                break;
            }

            if (result.isEmpty()) return null;
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.debug("Metadaten-Parsing fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    private void parseIso6709(String location, Map<String, Object> result) {
        Matcher m = ISO6709.matcher(location);
        if (!m.find()) return;
        try {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            result.put("gpsLat", Math.round(lat * 1e7) / 1e7);
            result.put("gpsLon", Math.round(lon * 1e7) / 1e7);
            if (m.group(3) != null) result.put("gpsAlt", Math.round(Double.parseDouble(m.group(3)) * 10.0) / 10.0);
        } catch (NumberFormatException ignored) {}
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private String firstNonNull(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = textOrNull(node, f);
            if (v != null) return v;
        }
        return null;
    }

    public static String toSnapshotPfad(String videoPfad) {
        return videoPfad + THUMB_SUFFIX;
    }
}
