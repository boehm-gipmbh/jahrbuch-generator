package de.jamsintown;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;

@Path("/api/v1/info")
@PermitAll
public class InfoResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response info() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("build-info.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {}

        String version = props.getProperty("build.version", "");
        if (version.isEmpty() || version.startsWith("${")) version = gitRevParse();

        String buildTime = props.getProperty("build.time", "");
        if (buildTime.startsWith("${")) buildTime = "";

        return Response.ok(Map.of("version", version, "buildTime", buildTime)).build();
    }

    private String gitRevParse() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"git", "rev-parse", "--short", "HEAD"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String sha = r.readLine();
                return sha != null ? sha.trim() : "dev";
            }
        } catch (IOException e) {
            return "dev";
        }
    }
}
