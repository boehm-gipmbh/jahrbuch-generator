package de.jamsintown;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
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

@ApplicationScoped
@Path("/api/v1/info")
@PermitAll
public class InfoResource {

    private String version;
    private String buildTime;

    @PostConstruct
    void init() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("build-info.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {}

        String v = props.getProperty("build.version", "");
        version = (v.isEmpty() || v.startsWith("${")) ? gitRevParse() : v;

        String t = props.getProperty("build.time", "");
        buildTime = t.startsWith("${") ? "" : t;
    }

    @GET
    @Blocking
    @Produces(MediaType.APPLICATION_JSON)
    public Response info() {
        return Response.ok(Map.of("version", version, "buildTime", buildTime)).build();
    }

    private String gitRevParse() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"git", "rev-parse", "--short", "HEAD"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String sha = r.readLine();
                return sha != null ? sha.trim() : "dev";
            }
        } catch (Exception e) {
            return "dev";
        }
    }
}
