package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.text.Text;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.User;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/clusters")
@RolesAllowed("user")
public class ClusterResource {

    @Inject
    UserService userService;

    public record LinkRequest(String typeA, Long idA, String typeB, Long idB) {}
    public record UnlinkRequest(String type, Long id) {}

    @POST
    @Path("/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> link(LinkRequest req) {
        return userService.getCurrentUser()
            .chain(user -> loadAndCheck(req.typeA(), req.idA(), user)
                .chain(cidA -> loadAndCheck(req.typeB(), req.idB(), user)
                    .chain(cidB -> {
                        if (cidA != null && cidA.equals(cidB))
                            return Uni.createFrom().item(Response.ok(cidA).build());
                        if (cidA == null && cidB == null)
                            return new Cluster().<Cluster>persistAndFlush()
                                .chain(c -> setClusterId(req.typeA(), req.idA(), c.id)
                                    .chain(v -> setClusterId(req.typeB(), req.idB(), c.id))
                                    .map(v -> Response.ok(c.id).build()));
                        if (cidA == null)
                            return setClusterId(req.typeA(), req.idA(), cidB)
                                .map(v -> Response.ok(cidB).build());
                        if (cidB == null)
                            return setClusterId(req.typeB(), req.idB(), cidA)
                                .map(v -> Response.ok(cidA).build());
                        Long winner = cidA, loser = cidB;
                        return Bild.<Bild>update("clusterId = ?1 WHERE clusterId = ?2", winner, loser)
                            .chain(v -> Text.<Text>update("clusterId = ?1 WHERE clusterId = ?2", winner, loser))
                            .chain(v -> Cluster.deleteById(loser))
                            .map(v -> Response.ok(winner).build());
                    })));
    }

    @DELETE
    @Path("/unlink")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> unlink(UnlinkRequest req) {
        return userService.getCurrentUser()
            .chain(user -> loadAndCheck(req.type(), req.id(), user))
            .chain(cid -> {
                if (cid == null) return Uni.createFrom().item(Response.noContent().build());
                return setClusterId(req.type(), req.id(), null)
                    .chain(v -> countClusterMembers(cid))
                    .chain(count -> count == 0
                        ? Cluster.deleteById(cid).map(v -> Response.noContent().build())
                        : Uni.createFrom().item(Response.noContent().build()));
            });
    }

    /** Lädt clusterId und prüft ob der User Zugriff hat (Eigentümer oder gleiche Gruppe). */
    private Uni<Long> loadAndCheck(String type, Long id, User user) {
        if ("BILD".equals(type))
            return Bild.<Bild>findById(id)
                .onItem().ifNull().failWith(() -> new NotFoundException("Bild " + id))
                .onItem().invoke(b -> checkAccess(b.user, b.group, user, id))
                .map(b -> b.clusterId);
        return Text.<Text>findById(id)
            .onItem().ifNull().failWith(() -> new NotFoundException("Text " + id))
            .onItem().invoke(t -> checkAccess(t.user, t.group, user, id))
            .map(t -> t.clusterId);
    }

    private void checkAccess(User owner, Gruppe itemGroup, User currentUser, Long id) {
        boolean isOwner = owner != null && owner.id.equals(currentUser.id);
        boolean inGroup = currentUser.activeGroup != null && itemGroup != null
            && currentUser.activeGroup.id.equals(itemGroup.id);
        if (!isOwner && !inGroup)
            throw new ForbiddenException("Access denied to item " + id);
    }

    private Uni<Integer> setClusterId(String type, Long id, Long clusterId) {
        if ("BILD".equals(type))
            return Bild.<Bild>update("clusterId = ?1 WHERE id = ?2", clusterId, id);
        return Text.<Text>update("clusterId = ?1 WHERE id = ?2", clusterId, id);
    }

    private Uni<Long> countClusterMembers(Long clusterId) {
        return Bild.<Bild>count("clusterId = ?1", clusterId)
            .chain(bc -> Text.<Text>count("clusterId = ?1", clusterId).map(tc -> bc + tc));
    }
}