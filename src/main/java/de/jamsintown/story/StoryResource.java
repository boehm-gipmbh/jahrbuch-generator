package de.jamsintown.story;

import de.jamsintown.user.Gruppe;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/api/v1/stories")
@RolesAllowed("user")
public class StoryResource {

  private final StoryService storyService;

  @Inject
  public StoryResource(StoryService storyService) {
    this.storyService = storyService;
  }
  @GET
  @Path("/{id}")
  public Uni<Story> getSingle(Long id) {
    return storyService.findById(id);
  }

  @GET
  public Uni<List<Story>> get() {
    return storyService.listForUser();
  }

  @GET
  @Path("/by-group/{groupId}")
  @RolesAllowed("admin")
  @WithSession
  public Uni<List<Story>> getByGroup(@PathParam("groupId") long groupId) {
    return Gruppe.<Gruppe>findById(groupId)
        .chain(g -> Story.<Story>find("group = ?1", Sort.by("orderPosition"), g).list());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  public Uni<Story> create(Story story) {
    return storyService.create(story);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{id}")
  public Uni<Story> update(@PathParam("id") long id, Story story) {
    story.id = id;
    return storyService.update(story);
  }

  @DELETE
  @Path("/{id}")
  public Uni<Void> delete(@PathParam("id") long id) {
    return storyService.delete(id);
  }

  @DELETE
  @Path("/{id}/cascade")
  public Uni<Void> deleteWithContent(@PathParam("id") long id) {
    return storyService.deleteWithContent(id);
  }

  @POST
  @Path("/restore")
  @Consumes(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  public Uni<Story> restoreByName(RestoreStoryRequest request) {
    return storyService.restoreByName(request.name, request.withContent);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{id}/reorder")
  public Uni<Void> reorder(@PathParam("id") long id, List<ReorderItem> items) {
    return storyService.reorder(id, items);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/reorder")
  public Uni<Void> reorderStories(List<Long> orderedIds) {
    return storyService.reorderStories(orderedIds);
  }

}
