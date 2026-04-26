package de.jamsintown.video;

import de.jamsintown.config.AppConfigService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Path("/api/v1/videos")
@RolesAllowed("user")
public class VideoResource {

    private final VideoService videoService;
    private final AppConfigService appConfigService;
    private final FfmpegService ffmpegService;
    private final String defaultCapturesPath;

    @Inject
    public VideoResource(VideoService videoService, AppConfigService appConfigService,
                          FfmpegService ffmpegService,
                          @ConfigProperty(name = "jahrbuch.captures.path") String defaultCapturesPath) {
        this.videoService = videoService;
        this.appConfigService = appConfigService;
        this.ffmpegService = ffmpegService;
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

    @POST
    @Path("/generate-thumbs")
    @RolesAllowed("admin")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<Response> generateThumbs() {
        return appConfigService.getValue("jahrbuch.captures.path")
                .map(p -> p != null ? p : defaultCapturesPath)
                .chain(capturesPath -> Video.<Video>listAll()
                        .map(videos -> {
                            Infrastructure.getDefaultWorkerPool().execute(() -> processVideos(videos, capturesPath));
                            return Response.accepted(Map.of("message", "Verarbeitung gestartet", "count", videos.size())).build();
                        }));
    }

    private void processVideos(java.util.List<Video> videos, String capturesPath) {
        int transcoded = 0, snapshots = 0, skipped = 0, errors = 0;
        for (Video v : videos) {
            if (v.pfad == null || v.deleted) { skipped++; continue; }
            java.nio.file.Path videoPath = Paths.get(capturesPath)
                    .resolve(v.pfad.replaceFirst("^/", "")).normalize();
            if (!videoPath.toFile().exists()) { skipped++; continue; }

            String codec = ffmpegService.detectCodec(videoPath);
            if (!"h264".equals(codec)) {
                boolean ok = ffmpegService.processVideo(videoPath);
                if (ok) transcoded++; else { errors++; continue; }
            }

            java.nio.file.Path thumbPath = videoPath.resolveSibling(videoPath.getFileName() + ".thumb.jpg");
            if (!thumbPath.toFile().exists()) {
                boolean ok = ffmpegService.generateSnapshot(videoPath);
                if (ok) snapshots++; else errors++;
            } else {
                skipped++;
            }
        }
        log.info("generate-thumbs abgeschlossen: transcoded={}, snapshots={}, skipped={}, errors={}",
                transcoded, snapshots, skipped, errors);
    }

}