package de.jamsintown.video;

import de.jamsintown.config.AppConfigService;
import io.quarkus.security.ForbiddenException;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.ObjectNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Path("/api/v1/videos")
@RolesAllowed("user")
public class VideoResource {

    private final VideoService videoService;
    private final AppConfigService appConfigService;
    private final String defaultCapturesPath;

    @Inject
    public VideoResource(VideoService videoService, AppConfigService appConfigService,
                          @ConfigProperty(name = "jahrbuch.captures.path") String defaultCapturesPath) {
        this.videoService = videoService;
        this.appConfigService = appConfigService;
        this.defaultCapturesPath = defaultCapturesPath;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<Video>> list() {
        return videoService.listForUser();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Video> update(@PathParam("id") Long id, Video video) {
        video.id = id;
        return videoService.update(video);
    }

    @PUT
    @Path("/{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Boolean> setComplete(@PathParam("id") long id, boolean complete) {
        return videoService.setComplete(id, complete);
    }

    @DELETE
    @Path("/{id}")
    public Uni<Void> delete(@PathParam("id") long id) {
        return videoService.softDelete(id);
    }

    @PUT
    @Path("/{id}/restore")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Video> restore(@PathParam("id") long id) {
        return videoService.restore(id);
    }

    @GET
    @Path("/papierkorb")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<Video>> papierkorb() {
        return videoService.listDeleted();
    }

    @DELETE
    @Path("/{id}/hard")
    public Uni<Void> hardDelete(@PathParam("id") long id) {
        return videoService.hardDelete(id);
    }

    @GET
    @Path("/extern/{pfad:.*}")
    public Uni<Response> streamVideo(@PathParam("pfad") String pfad,
                                      @HeaderParam("Range") String rangeHeader) {
        return appConfigService.getValue("jahrbuch.captures.path")
                .chain(configPath -> {
                    String capturesPath = configPath != null ? configPath : defaultCapturesPath;
                    java.nio.file.Path basePath = Paths.get(capturesPath).normalize();
                    java.nio.file.Path filePath = basePath.resolve(pfad).normalize();

                    if (!filePath.startsWith(basePath)) {
                        return Uni.createFrom().item(
                                Response.status(Response.Status.FORBIDDEN).entity("Zugriff verweigert").build());
                    }

                    return videoService.findByPfad("/" + pfad)
                            .chain(video -> buildVideoResponse(filePath.toFile(), rangeHeader))
                            .onFailure(e -> e instanceof ObjectNotFoundException || e instanceof ForbiddenException)
                            .recoverWithItem(Response.status(Response.Status.FORBIDDEN).entity("Zugriff verweigert").build());
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

        // Range: bytes=start-end
        try {
            long[] range = parseRange(rangeHeader, fileSize);
            long start = range[0];
            // cap chunk at 1 MB so we don't load huge video segments into heap
            long end = Math.min(range[1], start + 1024 * 1024 - 1);
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