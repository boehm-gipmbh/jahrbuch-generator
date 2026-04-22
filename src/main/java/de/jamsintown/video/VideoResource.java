package de.jamsintown.video;

import de.jamsintown.config.AppConfigService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

}