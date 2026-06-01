package de.jamsintown.pdf;

import de.jamsintown.bild.Bild;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
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
    @Produces(MediaType.APPLICATION_JSON)
    @WithSession
    public Uni<Response> outpaint(@PathParam("bildId") Long bildId) {
        if (!outpaintService.isConfigured()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Replicate API Key nicht konfiguriert"))
                    .build());
        }
        return Bild.<Bild>findById(bildId)
            .onItem().ifNull().failWith(() -> new NotFoundException("Bild nicht gefunden: " + bildId))
            .chain(bild -> outpaintService.outpaint(bild))
            .map(outpaintedPfad -> Response.ok(Map.of("outpaintedPfad", outpaintedPfad)).build())
            .onFailure().recoverWithItem(e ->
                Response.serverError().entity(Map.of("error", e.getMessage())).build());
    }
}
