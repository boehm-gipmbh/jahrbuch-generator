package de.jamsintown.story;

import de.jamsintown.bild.Bild;
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

//  @DELETE
//  @Path("/{id}")
//  public Uni<Void> delete(@PathParam("id") long id) {
//    return storyService.delete(id);
//  }

}
