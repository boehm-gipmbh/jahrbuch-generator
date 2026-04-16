package de.jamsintown.user;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.time.ZonedDateTime;
import java.util.List;

record ExtendRequest(ZonedDateTime expiresAt) {}
record ResendRequest(String recipientEmail) {}
record BatchEntry(String email, String role) {}
record BatchInvitationRequest(
    java.util.List<BatchEntry> entries,
    Long existingTokenId,
    ZonedDateTime expiresAt,
    String label,
    String defaultRole) {}
record BatchInvitationResult(String email, String status, String message) {}

@Path("/api/v1/users/invitations")
@RolesAllowed({"admin", "group-admin"})
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

  @PUT
  @Path("{id}/reactivate")
  public Uni<InvitationToken> reactivate(@PathParam("id") long id) {
    return service.reactivate(id);
  }

  @PUT
  @Path("{id}/extend")
  @Consumes(MediaType.APPLICATION_JSON)
  public Uni<InvitationToken> extend(@PathParam("id") long id, ExtendRequest body) {
    return service.extend(id, body.expiresAt());
  }

  @POST
  @Path("{id}/resend")
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(204)
  public Uni<Void> resend(@PathParam("id") long id, ResendRequest body) {
    return service.resend(id, body != null ? body.recipientEmail() : null);
  }

  @POST
  @Path("batch")
  @Consumes(MediaType.APPLICATION_JSON)
  public Uni<List<BatchInvitationResult>> batch(BatchInvitationRequest body) {
    return service.sendBatch(body);
  }

  @DELETE
  @Path("{id}")
  @ResponseStatus(204)
  public Uni<Void> delete(@PathParam("id") long id) {
    return service.delete(id);
  }
}