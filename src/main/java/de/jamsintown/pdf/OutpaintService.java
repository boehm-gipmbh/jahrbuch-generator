package de.jamsintown.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
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

    public Uni<String> outpaint(String bildPfad) {
        return Uni.createFrom()
            .item(() -> doOutpaint(bildPfad))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private String doOutpaint(String bildPfad) {
        String diskPath = capturesPath + bildPfad.replaceFirst("^/", "");
        String baseName = bildPfad.replaceFirst("^/", "").replaceFirst("\\.[^.]+$", "");
        String outpaintedPfad = "/" + baseName + "_outpainted.jpg";
        Path outpaintedDiskPath = Paths.get(capturesPath + baseName + "_outpainted.jpg");

        if (Files.exists(outpaintedDiskPath)) {
            log.info("Outpainted-Version bereits vorhanden: {}", outpaintedDiskPath);
            return outpaintedPfad;
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
            int offsetY = (canvasH - scaledH) / 2;

            // Canvas: gestrecktes+verwischtes Original als Hintergrund → gibt FLUX Farbkontext
            // (Himmel oben, Boden unten) und verhindert Graustufen-Artefakte
            Path bgPath = tempDir.resolve("bg.jpg");
            runProcess("convert", scaledPath.toString(),
                "-resize", scaledW + "x" + canvasH + "!",
                "-blur", "0x40",
                bgPath.toString());
            runProcess("convert", bgPath.toString(),
                scaledPath.toString(), "-gravity", "Center", "-composite",
                canvasPath.toString());

            // Maske: weiß = KI füllt, schwarz = Original beibehalten; Blur für weiche Übergänge
            runProcess("convert",
                "-size", scaledW + "x" + canvasH, "xc:white",
                "-fill", "black",
                "-draw", "rectangle 0," + offsetY + " " + (scaledW - 1) + "," + (offsetY + scaledH - 1),
                "-blur", "0x20",
                maskPath.toString());

            String imageB64 = Base64.getEncoder().encodeToString(Files.readAllBytes(canvasPath));
            String maskB64  = Base64.getEncoder().encodeToString(Files.readAllBytes(maskPath));

            String predictionId = createPrediction(imageB64, maskB64, scaledW, canvasH, apiKey);
            String resultUrl = pollUntilDone(predictionId, apiKey);
            downloadAndSave(resultUrl, outpaintedDiskPath);

            log.info("Outpainting abgeschlossen: {}", outpaintedDiskPath);
            return outpaintedPfad;

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

    private void deleteTempDir(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {}
    }

    private String createPrediction(String imageB64, String maskB64, int width, int height, String apiKey) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("input", new java.util.LinkedHashMap<>() {{
                put("image", "data:image/jpeg;base64," + imageB64);
                put("mask", "data:image/png;base64," + maskB64);
                put("prompt", "seamless outdoor background extension, natural sky and landscape continuation, same lighting and atmosphere, no ceiling, no indoor elements, no artifacts, photorealistic");
                put("width", width);
                put("height", height);
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

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Replicate API Fehler " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());

        if (json.has("output") && !json.get("output").isNull()) {
            return "__direct__:" + extractOutputUrl(json.get("output"));
        }
        return json.get("id").asText();
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
}
