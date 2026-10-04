package fr.fruityhedgeh0g.controllers;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.dtos.featureDtos.FeatureDto;
import fr.fruityhedgeh0g.dtos.featureDtos.FeatureSwitchDto;
import fr.fruityhedgeh0g.dtos.featureDtos.FeatureSwitchEntryDto;
import fr.fruityhedgeh0g.services.interfaces.publics.PublicFeatureService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

/**
 * Fonctionnalités: read by everyone, as the public site follows them (the gallery, sign-ups...);
 * switched by the Super admin only, who pulls these emergency levers (ADR 0009). Each switch goes to the Journal.
 */
@Path("/features")
@Produces(MediaType.APPLICATION_JSON)
public class FeatureController {

    /** The most Journal entries one page holds. */
    static final int MAX_PAGE_SIZE = 100;

    @Inject
    PublicFeatureService featureService;

    @Inject
    JsonWebToken token;

    @GET
    public @JsonView(Views.Basic.class) List<FeatureDto> getAll(){
        return featureService.listAll();
    }

    @PUT
    @Path("/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("super_admin")
    public @JsonView(Views.Basic.class) FeatureDto setActive(@PathParam("name") String name, @NotNull FeatureSwitchDto change){
        if (change.isActive() == null) throw new BadRequestException("A feature is turned on or off.");
        return featureService.switchLever(name, change.isActive(), change.reason(), me());
    }

    /** The Journal, newest first, one page at a time; never changed nor deleted from the site. */
    @GET
    @Path("/journal")
    @RolesAllowed("super_admin")
    public List<FeatureSwitchEntryDto> getJournal(@QueryParam("page") @DefaultValue("0") int page,
                                                  @QueryParam("size") @DefaultValue("20") int size){
        if (page < 0 || size < 1) throw new BadRequestException("A page is 0 or more, of 1 entry or more.");
        return featureService.journal(page, Math.min(size, MAX_PAGE_SIZE));
    }

    /** The Super admin's id, when the token has one. */
    private UUID me() {
        return token.getSubject() == null ? null : UUID.fromString(token.getSubject());
    }
}
