package de.jamsintown.story;

import de.jamsintown.bild.Bild;
import de.jamsintown.text.Text;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/clusters")
@RolesAllowed("user")
public class ClusterResource {

    public record LinkRequest(String typeA, Long idA, String typeB, Long idB) {}
    public record UnlinkRequest(String type, Long id) {}

    @POST
    @Path("/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> link(LinkRequest req) {
        return Uni.combine().all().unis(getClusterId(req.typeA(), req.idA()), getClusterId(req.typeB(), req.idB()))
            .asTuple()
            .chain(t -> {
                Long cidA = t.getItem1(), cidB = t.getItem2();
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
                // merge: move all of cidB into cidA, delete cidB
                Long winner = cidA, loser = cidB;
                return Bild.<Bild>update("clusterId = ?1 WHERE clusterId = ?2", winner, loser)
                    .chain(v -> Text.<Text>update("clusterId = ?1 WHERE clusterId = ?2", winner, loser))
                    .chain(v -> Cluster.deleteById(loser))
                    .map(v -> Response.ok(winner).build());
            });
    }

    @DELETE
    @Path("/unlink")
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> unlink(UnlinkRequest req) {
        return getClusterId(req.type(), req.id())
            .chain(cid -> {
                if (cid == null) return Uni.createFrom().item(Response.noContent().build());
                return setClusterId(req.type(), req.id(), null)
                    .chain(v -> countClusterMembers(cid))
                    .chain(count -> count == 0
                        ? Cluster.deleteById(cid).map(v -> Response.noContent().build())
                        : Uni.createFrom().item(Response.noContent().build()));
            });
    }

    private Uni<Long> getClusterId(String type, Long id) {
        if ("BILD".equals(type))
            return Bild.<Bild>findById(id).map(b -> b != null ? b.clusterId : null);
        return Text.<Text>findById(id).map(t -> t != null ? t.clusterId : null);
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