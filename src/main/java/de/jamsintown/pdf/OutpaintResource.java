package de.jamsintown.pdf;

import de.jamsintown.bild.Bild;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
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

        // Session 1: Bild laden
        return Panache.withSession(() ->
            Bild.<Bild>findById(bildId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Bild nicht gefunden: " + bildId))
                .map(bild -> new String[]{bild.pfad, bild.caption})
        )
        // Outpainting außerhalb Transaktion (dauert lange)
        .chain(data -> outpaintService.outpaint(data[0], customPrompt, data[1]))
        // Zurück auf Vert.x Event-Loop für Panache
        .emitOn(Infrastructure.getDefaultExecutor())
        // Transaktion 2: Caption speichern wenn neu/geändert
        .chain(result -> {
            String newCaption = result.effectivePrompt();
            if (newCaption == null) {
                return Uni.createFrom().item(result);
            }
            return Panache.withTransaction(() ->
                Bild.<Bild>findById(bildId)
                    .map(bild -> { bild.caption = newCaption; return result; })
            );
        })
        .map(result -> Response.ok(Map.of("outpaintedPfad", result.outpaintedPfad())).build())
        .onFailure().recoverWithItem(e ->
            Response.serverError().entity(Map.of("error", e.getMessage())).build());
    }

    @POST
    @Path("/{bildId}/caption")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> caption(@PathParam("bildId") Long bildId) {
        if (!outpaintService.isConfigured()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Replicate API Key nicht konfiguriert"))
                    .build());
        }

        // Session 1: vorhandene Caption prüfen
        return Panache.withSession(() ->
            Bild.<Bild>findById(bildId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Bild nicht gefunden: " + bildId))
                .map(bild -> new String[]{bild.pfad, bild.caption})
        )
        .chain(data -> {
            String pfad = data[0];
            String existing = data[1];
            if (existing != null && !existing.isBlank()) {
                // Caption bereits vorhanden — direkt zurückgeben
                return Uni.createFrom().item(existing);
            }
            // BLIP außerhalb Transaktion aufrufen, dann zurück auf Event-Loop
            return outpaintService.caption(pfad)
                .emitOn(Infrastructure.getDefaultExecutor())
                .chain(cap -> {
                    if (cap == null || cap.isBlank()) return Uni.createFrom().item("");
                    // Transaktion 2: Caption speichern
                    return Panache.withTransaction(() ->
                        Bild.<Bild>findById(bildId)
                            .map(bild -> { bild.caption = cap; return cap; })
                    );
                });
        })
        .map(cap -> Response.ok(Map.of("caption", cap)).build())
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
