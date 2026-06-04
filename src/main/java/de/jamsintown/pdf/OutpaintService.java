package de.jamsintown.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;

@Slf4j
@ApplicationScoped
public class OutpaintService {

    private static final double A4_RATIO = Math.sqrt(2); // Höhe/Breite
    private static final int TARGET_WIDTH = 768;
    private static final int TARGET_HEIGHT = (int) Math.round(TARGET_WIDTH * A4_RATIO / 16) * 16; // auf 16 gerundet

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String capturesPath;

    @ConfigProperty(name = "jahrbuch.replicate.api-key")
    java.util.Optional<String> replicateApiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newHttpClient();

    @Inject
    public OutpaintService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return replicateApiKey.isPresent() && !replicateApiKey.get().isBlank();
    }

    public boolean deleteOutpainted(String bildPfad) {
        String baseName = bildPfad.replaceFirst("^/", "").replaceFirst("\\.[^.]+$", "");
        Path outpaintedDiskPath = Paths.get(capturesPath + baseName + "_outpainted.jpg");
        if (!Files.exists(outpaintedDiskPath)) return false;
        try {
            Files.delete(outpaintedDiskPath);
            log.info("Outpainted-Datei gelöscht: {}", outpaintedDiskPath);
            return true;
        } catch (Exception e) {
            log.error("Fehler beim Löschen von {}: {}", outpaintedDiskPath, e.getMessage());
            throw new RuntimeException("Löschen fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public String captionBlocking(String bildPfad) {
        String diskPath = capturesPath + bildPfad.replaceFirst("^/", "");
        String apiKey = replicateApiKey.orElseThrow(() -> new RuntimeException("Replicate API Key nicht konfiguriert"));
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("outpaint-caption");
            Path scaledPath = tempDir.resolve("scaled.jpg");
            runProcess("convert", "-auto-orient", diskPath, "-resize", TARGET_WIDTH + "x", scaledPath.toString());
            String scaledB64 = Base64.getEncoder().encodeToString(Files.readAllBytes(scaledPath));
            SceneCaption sc = captionImage(scaledB64, apiKey);
            return sc != null ? sc.caption() : "";
        } catch (Exception e) {
            throw new RuntimeException("Captioning fehlgeschlagen: " + e.getMessage(), e);
        } finally {
            if (tempDir != null) deleteTempDir(tempDir);
        }
    }

    public OutpaintResult outpaintBlocking(String bildPfad, String customPrompt, String existingCaption) {
        return doOutpaint(bildPfad, customPrompt, existingCaption);
    }

    private OutpaintResult doOutpaint(String bildPfad, String customPrompt, String existingCaption) {
        String diskPath = capturesPath + bildPfad.replaceFirst("^/", "");
        String baseName = bildPfad.replaceFirst("^/", "").replaceFirst("\\.[^.]+$", "");
        String outpaintedPfad = "/" + baseName + "_outpainted.jpg";
        Path outpaintedDiskPath = Paths.get(capturesPath + baseName + "_outpainted.jpg");

        if (Files.exists(outpaintedDiskPath)) {
            log.info("Outpainted-Version bereits vorhanden: {}", outpaintedDiskPath);
            return new OutpaintResult(outpaintedPfad, customPrompt != null ? customPrompt : existingCaption);
        }

        String apiKey = replicateApiKey.orElseThrow(() -> new RuntimeException("Replicate API Key nicht konfiguriert"));
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("outpaint");
            Path scaledPath = tempDir.resolve("scaled.jpg");
            Path canvasPath = tempDir.resolve("canvas.jpg");
            Path maskPath  = tempDir.resolve("mask.png");

            // Auf TARGET_WIDTH skalieren (mit EXIF-Rotation)
            runProcess("convert", "-auto-orient", diskPath, "-resize", TARGET_WIDTH + "x", scaledPath.toString());

            // Skalierte Dimensionen ermitteln
            String dims = runProcessOutput("identify", "-format", "%wx%h", scaledPath.toString());
            String[] parts = dims.trim().split("x");
            int scaledW = Integer.parseInt(parts[0]);
            int scaledH = Integer.parseInt(parts[1]);

            int canvasH = TARGET_HEIGHT;
            int emptyVertSpace = canvasH - scaledH;

            if (emptyVertSpace <= 0) {
                // Bild füllt die Höhe bereits — mittig zuschneiden, kein KI-Fill nötig
                runProcess("convert", scaledPath.toString(),
                    "-gravity", "Center", "-crop", scaledW + "x" + canvasH + "+0+0", "+repage",
                    outpaintedDiskPath.toString());
                log.info("Outpainting übersprungen (Bild füllt Höhe bereits): {}", outpaintedDiskPath);
                return new OutpaintResult(outpaintedPfad, null);
            }

            // Prompt-Priorität: customPrompt > existingCaption > LLaVA-generiert
            // LLaVA früh aufrufen, um indoor-Flag für offsetY zu kennen
            boolean indoor = false;
            String usedCaption;
            if (customPrompt != null && !customPrompt.isBlank()) {
                usedCaption = customPrompt;
                log.info("Outpaint-Prompt: User-Eingabe");
            } else if (existingCaption != null && !existingCaption.isBlank()) {
                usedCaption = existingCaption;
                log.info("Outpaint-Prompt: vorhandene Caption");
            } else {
                String scaledB64 = Base64.getEncoder().encodeToString(Files.readAllBytes(scaledPath));
                SceneCaption sc = captionImage(scaledB64, apiKey);
                if (sc != null) {
                    indoor = sc.indoor();
                    usedCaption = sc.caption();
                } else {
                    usedCaption = null;
                }
                log.info("Outpaint-Prompt: LLaVA generiert (indoor={}): {}", indoor, usedCaption);
            }

            // offsetY: Innenraum mittig (50%), Außen oben (65% → mehr Himmel)
            int offsetY = (int) Math.round(emptyVertSpace * (indoor ? 0.5 : 0.65));

            // Canvas: Randpixel gestreckt als Farbkontext → verhindert FLUX-Halluzinationen bei schwarzem Fill
            int bottomFillH = emptyVertSpace - offsetY;
            Path topFillPath = tempDir.resolve("top_fill.jpg");
            runProcess("convert", scaledPath.toString(),
                "-crop", scaledW + "x2+0+0", "+repage",
                "-resize", scaledW + "x" + offsetY + "!",
                "-blur", "0x5",
                topFillPath.toString());
            Path bottomFillPath = tempDir.resolve("bottom_fill.jpg");
            runProcess("convert", scaledPath.toString(),
                "-crop", scaledW + "x2+0+" + (scaledH - 2), "+repage",
                "-resize", scaledW + "x" + bottomFillH + "!",
                "-blur", "0x5",
                bottomFillPath.toString());
            runProcess("convert",
                topFillPath.toString(), scaledPath.toString(), bottomFillPath.toString(),
                "-append", canvasPath.toString());

            // Maske: harte Kante — Modell binarisiert intern bei 0.5, Blur wäre kontraproduktiv
            runProcess("convert",
                "-size", scaledW + "x" + canvasH, "xc:white",
                "-fill", "black",
                "-draw", "rectangle 0," + offsetY + " " + (scaledW - 1) + "," + (offsetY + scaledH - 1),
                maskPath.toString());

            String effectivePrompt = (usedCaption != null && !usedCaption.isBlank())
                ? "seamlessly extend this photo: " + usedCaption + ", continue existing colors textures and atmosphere, no new subjects, photorealistic"
                : null;

            String imageB64 = Base64.getEncoder().encodeToString(Files.readAllBytes(canvasPath));
            String maskB64  = Base64.getEncoder().encodeToString(Files.readAllBytes(maskPath));

            String predictionId = createPrediction(imageB64, maskB64, apiKey, effectivePrompt, indoor);
            String resultUrl = pollUntilDone(predictionId, apiKey);
            downloadAndSave(resultUrl, outpaintedDiskPath);
            addWatermark(outpaintedDiskPath, "KI: FLUX Fill");

            log.info("Outpainting abgeschlossen: {}", outpaintedDiskPath);
            return new OutpaintResult(outpaintedPfad, usedCaption);

        } catch (Exception e) {
            log.error("Outpainting fehlgeschlagen für {}: {}", diskPath, e.getMessage(), e);
            throw new RuntimeException("Outpainting fehlgeschlagen: " + e.getMessage(), e);
        } finally {
            if (tempDir != null) deleteTempDir(tempDir);
        }
    }

    private void runProcess(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("ImageMagick-Fehler (" + cmd[0] + "): " + out);
    }

    private String runProcessOutput(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        return out;
    }

    private void addWatermark(Path imagePath, String text) {
        try {
            runProcess("convert", imagePath.toString(),
                "-gravity", "SouthEast",
                "-font", "DejaVu-Sans",
                "-pointsize", "18",
                "-fill", "rgba(255,255,255,0.55)",
                "-annotate", "+12+10", text,
                imagePath.toString());
            log.info("Watermark hinzugefügt: '{}'", text);
        } catch (Exception e) {
            log.warn("Watermark fehlgeschlagen (nicht kritisch): {}", e.getMessage());
        }
    }

    private void deleteTempDir(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {}
    }

    private static final String DEFAULT_PROMPT = "seamlessly extend photo background, continue existing colors textures and atmosphere, no new subjects, photorealistic";

    // LLaVA-13B: bekannte Fallback-Version falls Lookup fehlschlägt
    private static final String LLAVA_FALLBACK_VERSION = "e272157381e2a3bf12df3a8edd1f38d1dbd736bbb7437277c8b34175f8fce358";
    private static final String LLAVA_PROMPT =
        "Start your response with 'INDOOR:' if this photo was taken indoors, or 'OUTDOOR:' if outdoors. " +
        "Then describe the background environment in 1-2 short sentences. " +
        "For outdoor: sky, ground, landscape, colors, lighting. " +
        "For indoor: room type, walls, ceiling, floor, furniture, lighting. " +
        "Do not mention people or objects in the foreground.";

    private SceneCaption captionImage(String imageB64, String apiKey) {
        try {
            HttpResponse<String> versionResp = http.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("https://api.replicate.com/v1/models/yorickvp/llava-13b/versions"))
                    .header("Authorization", "Bearer " + apiKey).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            JsonNode versions = objectMapper.readTree(versionResp.body());
            String lookedUp = versions.path("results").path(0).path("id").asText(null);
            if (lookedUp == null) {
                log.warn("LLaVA: Version-Lookup fehlgeschlagen ({}), nutze Fallback", versionResp.statusCode());
            }
            final String llavVersion = lookedUp != null ? lookedUp : LLAVA_FALLBACK_VERSION;
            log.info("LLaVA version: {}", llavVersion);

            String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("version", llavVersion);
                put("input", new java.util.LinkedHashMap<>() {{
                    put("image", "data:image/jpeg;base64," + imageB64);
                    put("prompt", LLAVA_PROMPT);
                    put("max_tokens", 300);
                }});
            }});
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.replicate.com/v1/predictions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Prefer", "wait=30")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("LLaVA response status={} body={}", response.statusCode(), response.body());
            if (response.statusCode() >= 300) {
                log.warn("LLaVA API Fehler {}: {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body());
            if (json.has("output") && !json.get("output").isNull()) {
                SceneCaption sc = parseSceneCaption(assembleOutput(json.get("output")));
                log.info("LLaVA caption (direkt): indoor={} '{}'", sc.indoor(), sc.caption());
                return sc.caption().isBlank() ? null : sc;
            }
            String id = json.path("id").asText(null);
            if (id == null) {
                log.warn("LLaVA: kein output und keine id in Antwort: {}", response.body());
                return null;
            }
            log.info("LLaVA polling id={}", id);
            for (int i = 0; i < 20; i++) {
                Thread.sleep(3000);
                HttpResponse<String> poll = http.send(
                    HttpRequest.newBuilder().uri(URI.create("https://api.replicate.com/v1/predictions/" + id))
                        .header("Authorization", "Bearer " + apiKey).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                JsonNode p = objectMapper.readTree(poll.body());
                String status = p.path("status").asText();
                log.info("LLaVA poll {}: status={}", i, status);
                if ("succeeded".equals(status)) {
                    SceneCaption sc = parseSceneCaption(assembleOutput(p.get("output")));
                    log.info("LLaVA caption (poll): indoor={} '{}'", sc.indoor(), sc.caption());
                    return sc.caption().isBlank() ? null : sc;
                }
                if ("failed".equals(status) || "canceled".equals(status)) {
                    log.warn("LLaVA prediction {}: {}", status, p.path("error").asText());
                    return null;
                }
            }
            log.warn("LLaVA Timeout nach 60s für id={}", id);
        } catch (Exception e) {
            log.warn("Captioning fehlgeschlagen: {}", e.getMessage(), e);
        }
        return null;
    }

    private static SceneCaption parseSceneCaption(String raw) {
        if (raw == null || raw.isBlank()) return new SceneCaption(false, "");
        if (raw.startsWith("INDOOR:")) return new SceneCaption(true, raw.substring(7).trim());
        if (raw.startsWith("OUTDOOR:")) return new SceneCaption(false, raw.substring(8).trim());
        return new SceneCaption(false, raw.trim());
    }

    private static String assembleOutput(JsonNode output) {
        if (output == null) return "";
        if (output.isArray()) {
            StringBuilder sb = new StringBuilder();
            output.forEach(t -> sb.append(t.asText()));
            return sb.toString().trim();
        }
        return output.asText("").trim();
    }

    private String createPrediction(String imageB64, String maskB64, String apiKey, String customPrompt, boolean indoor) throws Exception {
        String prompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : DEFAULT_PROMPT;
        // Bei Innenräumen "ceiling" nicht unterdrücken — Decke soll erweitert werden
        String negativePrompt = indoor
            ? "new faces, new people, new persons, new bodies, duplicate people, text, watermark, blurry, artifacts, distorted, border, frame"
            : "new faces, new people, new persons, new bodies, duplicate people, ceiling, text, watermark, blurry, artifacts, distorted, border, frame";
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("input", new java.util.LinkedHashMap<>() {{
                put("image", "data:image/jpeg;base64," + imageB64);
                put("mask", "data:image/png;base64," + maskB64);
                put("prompt", prompt);
                put("negative_prompt", negativePrompt);
                put("num_inference_steps", 50);
                put("guidance", 30);
                put("output_format", "jpg");
                put("output_quality", 90);
            }});
        }});

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.replicate.com/v1/models/black-forest-labs/flux-fill-dev/predictions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Prefer", "wait=5")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        for (int attempt = 0; attempt < 4; attempt++) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                JsonNode err = objectMapper.readTree(response.body());
                int retryAfter = err.path("retry_after").asInt(15);
                log.warn("Replicate Rate-Limit, warte {}s (Versuch {}/4)", retryAfter, attempt + 1);
                Thread.sleep((retryAfter + 1) * 1000L);
                continue;
            }
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Replicate API Fehler " + response.statusCode() + ": " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            if (json.has("output") && !json.get("output").isNull()) {
                return "__direct__:" + extractOutputUrl(json.get("output"));
            }
            return json.get("id").asText();
        }
        throw new RuntimeException("Replicate Rate-Limit nach 4 Versuchen nicht überwunden");
    }

    private String pollUntilDone(String predictionId, String apiKey) throws Exception {
        if (predictionId.startsWith("__direct__:")) {
            return predictionId.substring("__direct__:".length());
        }

        String url = "https://api.replicate.com/v1/predictions/" + predictionId;
        for (int attempt = 0; attempt < 60; attempt++) {
            Thread.sleep(3000);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            String status = json.get("status").asText();
            if ("succeeded".equals(status)) {
                return extractOutputUrl(json.get("output"));
            } else if ("failed".equals(status) || "canceled".equals(status)) {
                throw new RuntimeException("Replicate Prediction " + status + ": " + json.path("error").asText());
            }
        }
        throw new RuntimeException("Replicate Timeout nach 3 Minuten");
    }

    private void downloadAndSave(String url, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Download fehlgeschlagen: " + response.statusCode());
        }
        Files.write(target, response.body());
    }

    private static String extractOutputUrl(JsonNode output) {
        if (output.isArray() && output.size() > 0) return output.get(0).asText();
        return output.asText();
    }

    private static String extractCaption(JsonNode output) {
        String raw = (output != null && output.isArray() && output.size() > 0)
            ? output.get(0).asText()
            : (output != null ? output.asText() : "");
        return raw.replaceFirst("^(Caption|Answer): ?", "").trim();
    }
}
