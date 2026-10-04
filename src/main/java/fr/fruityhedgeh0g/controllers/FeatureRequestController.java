package fr.fruityhedgeh0g.controllers;

import fr.fruityhedgeh0g.dtos.featureDtos.FeatureRequestDto;
import fr.fruityhedgeh0g.enums.FeatureEnum;
import fr.fruityhedgeh0g.services.FeatureLevers;
import fr.fruityhedgeh0g.services.FeatureRequestService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

/** Feature requests: written and read by the Bureau and above. */
@Path("/feature-requests")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("bureau")
public class FeatureRequestController {

    @Inject FeatureRequestService requestService;
    @Inject JsonWebToken token;
    @Inject FeatureLevers featureLevers;

    @GET
    public List<FeatureRequestDto> list(){
        return requestService.list();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public FeatureRequestDto create(@NotNull FeatureRequestDto.Input input){
        featureLevers.require(FeatureEnum.DEMANDES_FONCTIONNALITE);
        return requestService.create(UUID.fromString(token.getSubject()), input);
    }
}
