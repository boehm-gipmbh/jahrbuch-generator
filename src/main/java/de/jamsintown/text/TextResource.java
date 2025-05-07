package de.jamsintown.text;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/texte")
@RolesAllowed("user")
public class TextResource {
    private final TextService textService;

    public TextResource(TextService textService) {
        this.textService = textService;
    }

    @GET
    public Uni<List<Text>> get() {
        return textService.listForUser();
    }

    @GET
    @Path("/{id}")
    public Uni<Text> getSingle(Long id) {
        return textService.findByIdForUser(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public Uni<Text> create(Text text) {
        return textService.create(text);
    }

}
