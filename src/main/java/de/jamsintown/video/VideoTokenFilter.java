package de.jamsintown.video;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

/**
 * Erlaubt Video-Streaming via ?token= Query-Param statt Authorization-Header,
 * damit der Browser <video src="...?token=jwt"> nativ mit Range-Requests streamen kann.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 10)
public class VideoTokenFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!path.contains("/videos/extern/")) return;
        if (ctx.getHeaderString("Authorization") != null) return;

        String token = ctx.getUriInfo().getQueryParameters().getFirst("token");
        if (token != null && !token.isBlank()) {
            ctx.getHeaders().putSingle("Authorization", "Bearer " + token);
        }
    }
}
