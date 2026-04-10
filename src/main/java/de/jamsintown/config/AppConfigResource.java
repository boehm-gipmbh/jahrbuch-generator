package de.jamsintown.config;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/v1/admin/config")
@RolesAllowed("admin")
public class AppConfigResource {

  private final AppConfigService appConfigService;

  @Inject
  public AppConfigResource(AppConfigService appConfigService) {
    this.appConfigService = appConfigService;
  }

  /** GET /api/v1/admin/config — alle Konfigurationswerte */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Uni<List<AppConfig>> getAll() {
    return appConfigService.listAll();
  }

  /** PUT /api/v1/admin/config/{key} — einzelnen Wert setzen */
  @PUT
  @Path("{key}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Uni<AppConfig> setValue(@PathParam("key") String key, Map<String, String> body) {
    String value = body.get("value");
    if (value == null || value.isBlank()) {
      throw new BadRequestException("value field is required");
    }
    return appConfigService.setValue(key, value);
  }

  /** GET /api/v1/admin/config/{key} — einzelnen Wert abrufen */
  @GET
  @Path("{key}")
  @Produces(MediaType.APPLICATION_JSON)
  public Uni<Response> getValue(@PathParam("key") String key) {
    return appConfigService.getValue(key)
        .map(value -> {
          if (value == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
          }
          return Response.ok(Map.of("key", key, "value", value)).build();
        });
  }
}