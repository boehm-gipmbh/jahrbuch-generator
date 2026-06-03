package de.jamsintown.pdf;

import de.jamsintown.bild.Bild;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/pdf/outpaint")
@RolesAllowed("user")
public class OutpaintResource {

    private final OutpaintService outpaintService;

    @Inject
    public OutpaintResource(OutpaintService outpaintService) {
        this.outpaintService = outpaintService;
    }

    @POST
    @Path("/{bildId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> outpaint(@PathParam("bildId") Long bildId, Map<String, String> body) {
        if (!outpaintService.isConfigured()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Replicate API Key nicht konfiguriert"))
                    .build());
        }
        String customPrompt = body != null ? body.get("prompt") : null;
        return Panache.withSession(() ->
            Bild.<Bild>findById(bildId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Bild nicht gefunden: " + bildId))
                .map(bild -> bild.pfad)
        )
        .chain(pfad -> outpaintService.outpaint(pfad, customPrompt))
        .map(outpaintedPfad -> Response.ok(Map.of("outpaintedPfad", outpaintedPfad)).build())
        .onFailure().recoverWithItem(e ->
            Response.serverError().entity(Map.of("error", e.getMessage())).build());
    }

    @DELETE
    @Path("/{bildId}")
    @RolesAllowed("group-admin")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> deleteOutpainted(@PathParam("bildId") Long bildId) {
        return Panache.withSession(() ->
            Bild.<Bild>findById(bildId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Bild nicht gefunden: " + bildId))
                .map(bild -> bild.pfad)
        )
        .map(pfad -> {
            boolean deleted = outpaintService.deleteOutpainted(pfad);
            return deleted
                ? Response.ok(Map.of("deleted", true)).build()
                : Response.status(Response.Status.NOT_FOUND).entity(Map.of("deleted", false, "message", "Kein outpainted-Bild vorhanden")).build();
        })
        .onFailure().recoverWithItem(e ->
            Response.serverError().entity(Map.of("error", e.getMessage())).build());
    }
}
