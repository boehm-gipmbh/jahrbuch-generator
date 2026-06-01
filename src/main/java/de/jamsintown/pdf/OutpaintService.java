package de.jamsintown.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.jamsintown.bild.Bild;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@ApplicationScoped
public class OutpaintService {

    private static final double A4_RATIO = Math.sqrt(2); // Höhe/Breite
    private static final int TARGET_WIDTH = 768;
    private static final int TARGET_HEIGHT = (int) Math.round(TARGET_WIDTH * A4_RATIO / 16) * 16; // auf 16 gerundet

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String capturesPath;

    @ConfigProperty(name = "jahrbuch.replicate.api-key", defaultValue = "")
    String replicateApiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newHttpClient();

    @Inject
    public OutpaintService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return replicateApiKey != null && !replicateApiKey.isBlank();
    }

    public Uni<String> outpaint(Bild bild) {
        return Uni.createFrom()
            .item(() -> doOutpaint(bild))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private String doOutpaint(Bild bild) {
        String diskPath = capturesPath + bild.pfad.replaceFirst("^/", "");
        String baseName = bild.pfad.replaceFirst("^/", "").replaceFirst("\\.[^.]+$", "");
        String outpaintedPfad = "/" + baseName + "_outpainted.jpg";
        Path outpaintedDiskPath = Paths.get(capturesPath + baseName + "_outpainted.jpg");

        if (Files.exists(outpaintedDiskPath)) {
            log.info("Outpainted-Version bereits vorhanden: {}", outpaintedDiskPath);
            return outpaintedPfad;
        }

        try {
            BufferedImage orig = ImageIO.read(new File(diskPath));
            if (orig == null) throw new RuntimeException("Bild konnte nicht geladen werden: " + diskPath);

            // Auf TARGET_WIDTH skalieren
            int scaledW = TARGET_WIDTH;
            int scaledH = Math.max(1, orig.getHeight() * scaledW / orig.getWidth());

            BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(orig, 0, 0, scaledW, scaledH, null);
            g.dispose();

            // Portrait-Canvas erstellen (A4-Verhältnis)
            int canvasH = TARGET_HEIGHT;
            int offsetY = (canvasH - scaledH) / 2;

            BufferedImage canvas = new BufferedImage(scaledW, canvasH, BufferedImage.TYPE_INT_RGB);
            Graphics2D cg = canvas.createGraphics();
            cg.setColor(Color.WHITE);
            cg.fillRect(0, 0, scaledW, canvasH);
            cg.drawImage(scaled, 0, offsetY, null);
            cg.dispose();

            // Maske: weiß = füllen (Streifen oben/unten), schwarz = behalten (Originalbild)
            BufferedImage mask = new BufferedImage(scaledW, canvasH, BufferedImage.TYPE_INT_RGB);
            Graphics2D mg = mask.createGraphics();
            mg.setColor(Color.WHITE);
            mg.fillRect(0, 0, scaledW, canvasH);
            mg.setColor(Color.BLACK);
            mg.fillRect(0, offsetY, scaledW, scaledH);
            mg.dispose();

            String imageB64 = toBase64Jpeg(canvas);
            String maskB64 = toBase64Png(mask);

            String predictionId = createPrediction(imageB64, maskB64, scaledW, canvasH);
            String resultUrl = pollUntilDone(predictionId);
            downloadAndSave(resultUrl, outpaintedDiskPath);

            log.info("Outpainting abgeschlossen: {}", outpaintedDiskPath);
            return outpaintedPfad;

        } catch (Exception e) {
            log.error("Outpainting fehlgeschlagen für {}: {}", diskPath, e.getMessage(), e);
            throw new RuntimeException("Outpainting fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private String createPrediction(String imageB64, String maskB64, int width, int height) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("input", new java.util.LinkedHashMap<>() {{
                put("image", "data:image/jpeg;base64," + imageB64);
                put("mask", "data:image/png;base64," + maskB64);
                put("prompt", "seamless background extension, natural continuation of the scene, same lighting and style");
                put("width", width);
                put("height", height);
                put("output_format", "jpg");
                put("output_quality", 90);
            }});
        }});

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.replicate.com/v1/models/black-forest-labs/flux-fill-pro/predictions"))
            .header("Authorization", "Bearer " + replicateApiKey)
            .header("Content-Type", "application/json")
            .header("Prefer", "wait=5")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Replicate API Fehler " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());

        // Wenn Prefer: wait=5 funktioniert hat, ist das Ergebnis direkt da
        if (json.has("output") && !json.get("output").isNull()) {
            String url = json.get("output").asText();
            return "__direct__:" + url;
        }
        return json.get("id").asText();
    }

    private String pollUntilDone(String predictionId) throws Exception {
        if (predictionId.startsWith("__direct__:")) {
            return predictionId.substring("__direct__:".length());
        }

        String url = "https://api.replicate.com/v1/predictions/" + predictionId;
        for (int attempt = 0; attempt < 60; attempt++) {
            Thread.sleep(3000);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + replicateApiKey)
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            String status = json.get("status").asText();
            if ("succeeded".equals(status)) {
                return json.get("output").asText();
            } else if ("failed".equals(status) || "canceled".equals(status)) {
                throw new RuntimeException("Replicate Prediction " + status + ": " + json.path("error").asText());
            }
        }
        throw new RuntimeException("Replicate Timeout nach 3 Minuten");
    }

    private void downloadAndSave(String url, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Download fehlgeschlagen: " + response.statusCode());
        }
        Files.write(target, response.body());
    }

    private static String toBase64Jpeg(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String toBase64Png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
