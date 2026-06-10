package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.text.Text;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/story-items/link")
@RolesAllowed("group-admin")
public class StoryItemLinkResource {

    public record LinkRequest(Long textId, Long bildId) {}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> link(LinkRequest req) {
        return TextBildLink.<TextBildLink>find("text.id = ?1 AND bild.id = ?2", req.textId(), req.bildId()).firstResult()
            .chain(existing -> {
                if (existing != null) return Uni.createFrom().item(Response.ok(existing).build());
                return Text.<Text>findById(req.textId())
                    .chain(text -> Bild.<Bild>findById(req.bildId())
                        .chain(bild -> {
                            if (text == null || bild == null) throw new NotFoundException();
                            TextBildLink link = new TextBildLink();
                            link.text = text;
                            link.bild = bild;
                            return link.<TextBildLink>persistAndFlush()
                                .map(saved -> Response.ok(saved).build());
                        }));
            });
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> unlink(LinkRequest req) {
        return TextBildLink.<TextBildLink>find("text.id = ?1 AND bild.id = ?2", req.textId(), req.bildId()).firstResult()
            .chain(link -> {
                if (link == null) return Uni.createFrom().item(Response.noContent().build());
                return link.delete().map(v -> Response.noContent().build());
            });
    }

    @GET
    @Path("/by-story/{storyId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"group-admin", "user"})
    @io.quarkus.hibernate.reactive.panache.common.WithSession
    public Uni<java.util.List<LinkDTO>> getLinksByStory(@PathParam("storyId") Long storyId) {
        return TextBildLink.<TextBildLink>find(
            "text.story.id = ?1 OR bild.story.id = ?1", storyId
        ).list()
        .map(links -> links.stream()
            .map(l -> new LinkDTO(l.text.id, l.bild.id))
            .distinct().toList());
    }

    public record LinkDTO(Long textId, Long bildId) {}
}