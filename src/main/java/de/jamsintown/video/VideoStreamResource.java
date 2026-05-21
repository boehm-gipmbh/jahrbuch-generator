package de.jamsintown.video;

import de.jamsintown.config.AppConfigService;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Path("/api/v1/videos")
@PermitAll
public class VideoStreamResource {

    private final AppConfigService appConfigService;
    private final MinioService minioService;
    private final JWTParser jwtParser;
    private final String defaultCapturesPath;

    @Inject
    public VideoStreamResource(AppConfigService appConfigService,
                                MinioService minioService,
                                JWTParser jwtParser,
                                @ConfigProperty(name = "jahrbuch.captures.path") String defaultCapturesPath) {
        this.appConfigService = appConfigService;
        this.minioService = minioService;
        this.jwtParser = jwtParser;
        this.defaultCapturesPath = defaultCapturesPath;
    }

    @GET
    @Path("/extern/{pfad:.*}")
    public Uni<Response> streamVideo(@PathParam("pfad") String pfad,
                                      @HeaderParam("Range") String rangeHeader,
                                      @QueryParam("token") String tokenParam) {
        if (tokenParam == null || tokenParam.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        try {
            jwtParser.parse(tokenParam);
        } catch (ParseException e) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        return appConfigService.getValue("jahrbuch.captures.path")
                .chain(configPath -> {
                    String capturesPath = configPath != null ? configPath : defaultCapturesPath;
                    java.nio.file.Path basePath = Paths.get(capturesPath).normalize();
                    java.nio.file.Path filePath = basePath.resolve(pfad).normalize();

                    if (!filePath.startsWith(basePath)) {
                        return Uni.createFrom().item(
                                Response.status(Response.Status.FORBIDDEN).entity("Zugriff verweigert").build());
                    }

                    File localFile = filePath.toFile();
                    if (localFile.exists() && localFile.isFile()) {
                        return buildVideoResponse(localFile, rangeHeader);
                    }
                    return buildMinioResponse(pfad, rangeHeader);
                });
    }

    private Uni<Response> buildMinioResponse(String pfad, String rangeHeader) {
        String objectKey = pfad.startsWith("/") ? pfad.substring(1) : pfad;
        return minioService.getObjectSize(objectKey).chain(size -> {
            if (size < 0) {
                return Uni.createFrom().item(
                        Response.status(Response.Status.NOT_FOUND).entity("Datei nicht gefunden").build());
            }
            return minioService.download(objectKey, rangeHeader).map(stream -> {
                if (rangeHeader != null && !rangeHeader.isBlank()) {
                    try {
                        long[] range = parseRange(rangeHeader, size);
                        long start = range[0], end = range[1], length = end - start + 1;
                        return Response.status(206)
                                .header("Content-Type", "video/mp4")
                                .header("Accept-Ranges", "bytes")
                                .header("Content-Range", "bytes " + start + "-" + end + "/" + size)
                                .header("Content-Length", length)
                                .entity(stream)
                                .build();
                    } catch (IllegalArgumentException e) {
                        return Response.status(416)
                                .header("Content-Range", "bytes */" + size)
                                .build();
                    }
                }
                return Response.ok(stream)
                        .header("Content-Type", "video/mp4")
                        .header("Accept-Ranges", "bytes")
                        .header("Content-Length", size)
                        .build();
            });
        });
    }

    private Uni<Response> buildVideoResponse(File file, String rangeHeader) {
        if (!file.exists() || !file.isFile()) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.NOT_FOUND).entity("Datei nicht gefunden").build());
        }

        String mimeType;
        try {
            mimeType = Files.probeContentType(file.toPath());
        } catch (IOException e) {
            mimeType = null;
        }
        if (mimeType == null) mimeType = "video/mp4";

        long fileSize = file.length();

        if (rangeHeader == null || rangeHeader.isBlank()) {
            return Uni.createFrom().item(Response.ok(file)
                    .header("Content-Type", mimeType)
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Length", fileSize)
                    .build());
        }

        try {
            long[] range = parseRange(rangeHeader, fileSize);
            long start = range[0];
            // Offene Range-Requests (bytes=X-): Browser sucht moov-Atom → 10 MB Cap
            // Explizite Range-Requests (bytes=X-Y): Seek während Wiedergabe → 2 MB Cap
            String rangeSpec = rangeHeader.substring(6);
            boolean openEnded = rangeSpec.split("-", 2)[1].isEmpty() || rangeSpec.startsWith("-");
            long cap = openEnded ? 10L * 1024 * 1024 : 2L * 1024 * 1024;
            long end = Math.min(range[1], start + cap - 1);
            long length = end - start + 1;

            byte[] chunk = readChunk(file, start, length);

            return Uni.createFrom().item(Response.status(206)
                    .header("Content-Type", mimeType)
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
                    .header("Content-Length", length)
                    .entity(chunk)
                    .build());
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(Response.status(416)
                    .header("Content-Range", "bytes */" + fileSize)
                    .build());
        } catch (IOException e) {
            log.error("Fehler beim Video-Streaming: {}", e.getMessage(), e);
            return Uni.createFrom().item(Response.serverError().entity("Streaming-Fehler").build());
        }
    }

    private long[] parseRange(String rangeHeader, long fileSize) {
        if (!rangeHeader.startsWith("bytes=")) throw new IllegalArgumentException("Invalid range");
        String spec = rangeHeader.substring(6);
        String[] parts = spec.split("-", 2);
        long start, end;
        if (parts[0].isEmpty()) {
            long suffix = Long.parseLong(parts[1]);
            start = fileSize - suffix;
            end = fileSize - 1;
        } else {
            start = Long.parseLong(parts[0]);
            end = parts[1].isEmpty() ? fileSize - 1 : Long.parseLong(parts[1]);
        }
        if (start < 0 || end >= fileSize || start > end) throw new IllegalArgumentException("Range out of bounds");
        return new long[]{start, end};
    }

    private byte[] readChunk(File file, long start, long length) throws IOException {
        byte[] buffer = new byte[(int) length];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            int totalRead = 0;
            while (totalRead < buffer.length) {
                int read = raf.read(buffer, totalRead, buffer.length - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            if (totalRead < buffer.length) {
                byte[] result = new byte[totalRead];
                System.arraycopy(buffer, 0, result, 0, totalRead);
                return result;
            }
        }
        return buffer;
    }
}
