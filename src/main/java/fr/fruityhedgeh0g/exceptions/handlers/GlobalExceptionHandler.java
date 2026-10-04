package fr.fruityhedgeh0g.exceptions.handlers;

import fr.fruityhedgeh0g.exceptions.DuplicateResourceException;
import fr.fruityhedgeh0g.exceptions.FeatureOffException;
import fr.fruityhedgeh0g.exceptions.ForbiddenActionException;
import fr.fruityhedgeh0g.exceptions.ForbiddenRoleChangeException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.NotImplementedYetException;
import fr.fruityhedgeh0g.exceptions.PhoneRequiredException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;


public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public RestResponse<Void> mapException(Exception x) {

        return RestResponse.status(Response.Status.INTERNAL_SERVER_ERROR);
    }

    /** Framework errors (404 unknown path, 405 method, 415...) keep their own status. */
    @ServerExceptionMapper
    public Response mapWebApplicationException(WebApplicationException x) {
        return x.getResponse();
    }

    @ServerExceptionMapper
    public RestResponse<Void> mapDuplicateResourceException(DuplicateResourceException x) {
        return RestResponse.status(Response.Status.CONFLICT);
    }

    @ServerExceptionMapper
    public RestResponse<Void> mapUnknownResourceException(UnknownResourceException x) {
        return RestResponse.status(Response.Status.NOT_FOUND);
    }

    @ServerExceptionMapper
    public RestResponse<Void> mapForbiddenActionException(ForbiddenActionException x) {
        return RestResponse.status(Response.Status.FORBIDDEN);
    }

    @ServerExceptionMapper
    public RestResponse<Void> mapForbiddenRoleChangeException(ForbiddenRoleChangeException x) {
        return RestResponse.status(Response.Status.FORBIDDEN);
    }

    /** The front end reads {@code error} to ask for the phone number before retrying. */
    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapPhoneRequiredException(PhoneRequiredException x) {
        return RestResponse.ResponseBuilder.<Map<String, String>>create(422).entity(Map.of("error", "phone-required")).build();
    }

    /** The front end reads {@code feature} to say what is suspended (ADR 0009). */
    @ServerExceptionMapper
    public RestResponse<Map<String, String>> mapFeatureOffException(FeatureOffException x) {
        return RestResponse.ResponseBuilder.<Map<String, String>>create(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "feature-off", "feature", x.getFeature().id())).build();
    }

    @ServerExceptionMapper
    public RestResponse<Void> mapInvalidResourceException(InvalidResourceException x) {
        return RestResponse.status(Response.Status.BAD_REQUEST);
    }

    @ServerExceptionMapper
    public RestResponse<Void> mapNotImplementedYetException(NotImplementedYetException x) {
        return RestResponse.status(Response.Status.NOT_IMPLEMENTED);
    }


}
