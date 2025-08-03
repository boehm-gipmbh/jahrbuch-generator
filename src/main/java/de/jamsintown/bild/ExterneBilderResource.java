package de.jamsintown.bild;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;

@Path("/api/bilder/extern")
public class ExterneBilderResource {

    @GET
    @Path("/{pfad:.*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getBild(@PathParam("pfad") String pfad) {
        try {
            File file = new File("/home/dboehm/jahrbuch-generator/captures/" + pfad);
            if (!file.exists() || !file.isFile()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(file).header("Content-Disposition",
                    "attachment; filename=" + file.getName()).build();
        } catch (Exception e) {
            return Response.serverError().build();
        }
    }
}
