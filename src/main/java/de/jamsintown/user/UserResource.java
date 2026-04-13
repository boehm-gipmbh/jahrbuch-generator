package de.jamsintown.user;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/users")
@RolesAllowed("admin")
public class UserResource {

  private final UserService userService;
  private final GruppeService gruppeService;

  @Inject
  public UserResource(UserService userService, GruppeService gruppeService) {
    this.userService = userService;
    this.gruppeService = gruppeService;
  }

  @GET
  public Uni<List<User>> get() {
    return userService.list();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  public Uni<User> create(User user) {
    return userService.create(user);
  }

  @GET
  @Path("{id}")
  public Uni<User> get(@PathParam("id") long id) {
    return userService.findById(id);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("{id}")
  public Uni<User> update(@PathParam("id") long id, User user) {
    user.id = id;
    return userService.update(user);
  }

  @DELETE
  @Path("{id}")
  @ResponseStatus(204)
  @RolesAllowed({"admin", "group-admin"})
  public Uni<Void> delete(@PathParam("id") long id) {
    return userService.delete(id);
  }

  @PUT
  @Path("{id}/promote/{groupId}")
  @RolesAllowed({"admin", "group-admin"})
  public Uni<User> promoteToGroupAdmin(
      @PathParam("id") long id,
      @PathParam("groupId") long groupId) {
    return userService.promoteToGroupAdmin(id, groupId);
  }

  @PUT
  @Path("{id}/demote")
  @RolesAllowed({"admin", "group-admin"})
  public Uni<User> demoteFromGroupAdmin(@PathParam("id") long id) {
    return userService.demoteFromGroupAdmin(id);
  }

  @POST
  @Path("{id}/remind")
  @ResponseStatus(204)
  @RolesAllowed({"admin", "group-admin"})
  public Uni<Void> sendReminder(@PathParam("id") long id) {
    return userService.sendReminder(id);
  }

  @PUT
  @Path("{id}/deactivate")
  @RolesAllowed({"admin", "group-admin"})
  public Uni<User> deactivate(@PathParam("id") long id) {
    return userService.deactivate(id);
  }

  @PUT
  @Path("{id}/reactivate")
  @RolesAllowed({"admin", "group-admin"})
  public Uni<User> reactivate(@PathParam("id") long id) {
    return userService.reactivate(id);
  }

  @GET
  @Path("self")
  @RolesAllowed("user")
  public Uni<User> getCurrentUser() {
    return userService.getCurrentUser();
  }

  @PUT
  @Path("self/password")
  @RolesAllowed("user")
  public Uni<User> changePassword(PasswordChange passwordChange) {
    return userService
      .changePassword(passwordChange.currentPassword(), passwordChange.newPassword());
  }

  @PUT
  @Path("self/active-group/{groupId}")
  @RolesAllowed("user")
  public Uni<User> setActiveGroup(@PathParam("groupId") long groupId) {
    return userService.getCurrentUser()
      .chain(user -> gruppeService.setActiveGroup(user, groupId));
  }

  @DELETE
  @Path("self/active-group")
  @RolesAllowed("user")
  public Uni<User> clearActiveGroup() {
    return userService.getCurrentUser()
      .chain(user -> gruppeService.clearActiveGroup(user));
  }

}
