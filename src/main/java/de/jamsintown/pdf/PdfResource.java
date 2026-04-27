package de.jamsintown.pdf;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/pdf")
public class PdfResource {

    private final PdfService pdfService;

    @Inject
    public PdfResource(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GET
    @Path("/{groupId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Uni<Response> generatePdf(@PathParam("groupId") Long groupId) {
        return pdfService.generateForGroup(groupId)
            .map(bytes -> Response.ok(bytes)
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"jahrbuch-" + groupId + ".pdf\"")
                .build());
    }
}