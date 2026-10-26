package fr.fruityhedgeh0g.controllers;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;

/**
 * Where the site sends the browser to log in. Being protected, the OIDC web-app flow sends the browser to
 * Keycloak first, then back here (query included), and this sends it back to the page it came from.
 * {@code ?prompt=create} is forwarded to Keycloak, which then opens its registration form.
 * Logging out is {@code quarkus.oidc.logout.path}.
 */
@Path("/auth")
public class AuthController {

    @GET
    @Path("/login")
    @Authenticated
    public Response login(@QueryParam("redirect") String redirect, @Context UriInfo uriInfo) {
        // Absolute: a relative Location would be resolved against the API root (/api/), not the site's
        return Response.seeOther(uriInfo.getBaseUri().resolve(sitePath(redirect))).build();
    }

    /** Only a path of this site, so the login cannot be used to send someone elsewhere. */
    static String sitePath(String redirect) {
        if (redirect == null || !redirect.startsWith("/") || redirect.startsWith("//") || redirect.contains("\\")) {
            return "/";
        }
        try {
            URI uri = URI.create(redirect);
            return uri.getScheme() == null && uri.getAuthority() == null ? redirect : "/";
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
