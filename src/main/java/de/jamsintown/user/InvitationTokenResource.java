package de.jamsintown.user;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/users/invitations")
@RolesAllowed("admin")
public class InvitationTokenResource {

  private final InvitationTokenService service;

  @Inject
  public InvitationTokenResource(InvitationTokenService service) {
    this.service = service;
  }

  @GET
  public Uni<List<InvitationToken>> list() {
    return service.list();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  public Uni<InvitationToken> create(InvitationToken token) {
    return service.create(token);
  }

  @PUT
  @Path("{id}/deactivate")
  public Uni<InvitationToken> deactivate(@PathParam("id") long id) {
    return service.deactivate(id);
  }

  @DELETE
  @Path("{id}")
  @ResponseStatus(204)
  public Uni<Void> delete(@PathParam("id") long id) {
    return service.delete(id);
  }
}