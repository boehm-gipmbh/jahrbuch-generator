package de.jamsintown.video;

import io.quarkus.vertx.http.runtime.filters.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Erlaubt Video-Streaming via ?token= Query-Param statt Authorization-Header,
 * damit der Browser <video src="...?token=jwt"> nativ mit Range-Requests streamen kann.
 * Muss auf Vert.x-Ebene laufen (vor der JWT-Prüfung).
 */
@ApplicationScoped
public class VideoTokenFilter {

    @RouteFilter(200)
    public void filter(RoutingContext rc) {
        String path = rc.request().path();
        if (path.contains("/videos/extern/") && rc.request().getHeader("Authorization") == null) {
            String token = rc.request().getParam("token");
            if (token != null && !token.isBlank()) {
                rc.request().headers().set("Authorization", "Bearer " + token);
            }
        }
        rc.next();
    }
}
